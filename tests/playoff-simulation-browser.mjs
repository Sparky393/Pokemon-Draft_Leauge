/** Real site/UI with snapshots exported by PlayoffSimulationTest; no server/database/network. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const fixtureUrl = new URL("../target/playoff-simulation-snapshots.json", import.meta.url);
const { scenarios } = JSON.parse(readFileSync(fixtureUrl, "utf8"));
assert.equal(scenarios.length, 18, "Run PlayoffSimulationTest to export 18 verified fixture scenarios first");
const staticRoot = new URL("../src/main/resources/static/", import.meta.url);
const origin = "http://league.test";
const settings = { leagueName: "Playoff Simulation", currentWeek: 14, regularSeasonWeeks: 13,
  draftOpen: false, draftFinalized: true, snakeDraft: true };
const browser = await chromium.launch({ headless: true });
let screens = 0, scoresSubmitted = 0;

try {
  for (const width of [1440, 390]) {
    for (const role of ["MANAGER", "GUEST"]) {
      const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 } });
      const page = await context.newPage();
      const errors = [], unexpected = [], mutations = [];
      let scenarioIndex = 0, snapshotIndex = 0;
      const snapshot = () => scenarios[scenarioIndex].snapshots[snapshotIndex];
      const user = { id: role === "GUEST" ? null : 999, username: role === "GUEST" ? "Guest" : "Simulation Manager", role, teamId: null };
      page.on("pageerror", (error) => errors.push(error.message));
      await page.route("**/*", async (route) => {
        const req = route.request(), url = new URL(req.url());
        const json = (body, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
        if (url.origin !== origin) {
          unexpected.push(`External request: ${url.origin}`);
          return route.abort();
        }
        const path = url.pathname;
        if (path.startsWith("/api/")) {
          if (req.method() === "POST" && path.startsWith("/api/manager/results/")) {
            const next = scenarios[scenarioIndex].snapshots[snapshotIndex + 1];
            try {
              assert.equal(role, "MANAGER");
              assert.ok(next?.result, "No more expected playoff results");
              assert.equal(path, `/api/manager/results/${next.result.id}`);
              assert.deepEqual(req.postDataJSON(), { homeScore: next.result.homeScore, awayScore: next.result.awayScore });
              assert.equal(req.headers()["x-requested-with"], "XMLHttpRequest");
              mutations.push({ scenarioIndex, ...next.result });
              snapshotIndex++;
              return json(null);
            } catch (error) {
              unexpected.push(error.message);
              return json({ message: error.message }, 400);
            }
          }
          switch (path) {
            case "/api/auth/me": return json(user);
            case "/api/dashboard/view": return json({ user, teams: snapshot().teams, settings, currentDraftTeam: null });
            case "/api/teams": return json(snapshot().teams);
            case "/api/settings": return json(settings);
            case "/api/playoffs": return json(snapshot().bracket);
            case "/api/weeks/locks": return json({});
            case "/api/notifications": return json([]);
            default:
              unexpected.push(`${req.method()} ${path}`);
              return json({ message: "Unexpected request in playoff fixture" }, 403);
          }
        }
        const asset = path === "/" ? "index.html" : path.slice(1);
        if (asset === "favicon.ico") return route.fulfill({ status: 204 });
        if (!/^(index\.html|(?:js|css)\/[a-z-]+\.(?:js|css))$/.test(asset)) {
          unexpected.push(path);
          return route.fulfill({ status: 404 });
        }
        return route.fulfill({ body: readFileSync(new URL(asset, staticRoot)),
          contentType: asset.endsWith(".js") ? "text/javascript" : asset.endsWith(".css") ? "text/css" : "text/html" });
      });

      await page.goto(origin);
      await page.locator("#page-home h1").waitFor();
      await page.locator('[data-page="playoffs"]').click();
      await page.locator("#page-playoffs h1").waitFor();

      const renderedGames = () => page.locator("#page-playoffs .playoff-game").evaluateAll((cards) => cards.map((card) => {
        const versus = card.querySelector(".season-versus").children;
        return { label: card.querySelector(".playoff-game-label").textContent.trim(),
          home: versus[0].querySelector(".team-brand-text").textContent.trim(), score: versus[1].textContent.trim(), away: versus[2].querySelector(".team-brand-text").textContent.trim(),
          awaiting: card.classList.contains("awaiting"), saveId: card.querySelector(".save-result")?.dataset.id || null };
      }));
      const expectedGames = () => snapshot().bracket.games.map((game) => ({
        label: `${game.division} · ${game.round}`, home: game.homeTeam, away: game.awayTeam,
        score: game.played ? `${game.homeScore} – ${game.awayScore}` : "vs", awaiting: !game.ready,
        saveId: role === "MANAGER" && game.ready ? String(game.id) : null,
      }));
      const assertScreen = async () => {
        assert.deepEqual(await renderedGames(), expectedGames(), `seed=${scenarios[scenarioIndex].seed} mask=${scenarios[scenarioIndex].outcomeMask} step=${snapshotIndex} ${width}px ${role}`);
        assert.match(await page.locator("#page-playoffs").innerText(), /Kanto: 1 vs 4 and 2 vs 3\. Johto: 1 vs 4 and 2 vs 3\./);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, "No page-wide horizontal overflow");
        if (role === "GUEST") assert.equal(await page.locator("#page-playoffs .season-score-controls").count(), 0);
        assert.deepEqual(errors, []);
        assert.deepEqual(unexpected, []);
        screens++;
      };
      const renderSnapshot = () => page.evaluate(async () => { await refreshBase(); await renderPlayoffs(); });

      for (scenarioIndex = 0; scenarioIndex < scenarios.length; scenarioIndex++) {
        snapshotIndex = 0;
        await renderSnapshot();
        await assertScreen(); // Initial 1-v-4 / 2-v-3 placeholders, no materialized fixtures.
        snapshotIndex = 1;
        await renderSnapshot();
        await assertScreen(); // Actual seeds after the last regular result.

        if (role === "MANAGER" && scenarioIndex === 0) {
          const id = snapshot().bracket.games[0].id;
          await page.locator(`.score-home[data-id="${id}"]`).fill("3");
          await page.locator(`.score-away[data-id="${id}"]`).fill("0");
          await page.locator(`.save-result[data-id="${id}"]`).click();
          await page.getByText("Playoff scores must be 2–0, 2–1, 0–2 or 1–2.", { exact: true }).waitFor();
          assert.equal(mutations.length, 0, "Invalid playoff scores never reach the API");
          assert.equal(snapshotIndex, 1);
        }

        for (let nextIndex = 2; nextIndex < scenarios[scenarioIndex].snapshots.length; nextIndex++) {
          if (role === "MANAGER") {
            const result = scenarios[scenarioIndex].snapshots[nextIndex].result;
            await page.locator(`.score-home[data-id="${result.id}"]`).fill(String(result.homeScore));
            await page.locator(`.score-away[data-id="${result.id}"]`).fill(String(result.awayScore));
            const refreshed = page.waitForResponse((response) => response.url() === `${origin}/api/playoffs`);
            await page.locator(`.save-result[data-id="${result.id}"]`).click();
            await refreshed;
            await page.waitForFunction(({ id, score }) => {
              const button = document.querySelector(`.save-result[data-id="${id}"]`);
              return button?.closest(".playoff-game")?.querySelector(".season-versus > span")?.textContent.trim() === score;
            }, { id: result.id, score: `${result.homeScore} – ${result.awayScore}` });
          } else {
            snapshotIndex = nextIndex; // Another manager saved this result; guest only reads it.
            await renderSnapshot();
          }
          assert.equal(snapshotIndex, nextIndex);
          await assertScreen();
        }
      }
      const expectedPosts = role === "MANAGER" ? scenarios.length * 7 : 0;
      assert.equal(mutations.length, expectedPosts);
      scoresSubmitted += mutations.length;
      await page.screenshot({ path: new URL(`../target/playoff-simulation-${role.toLowerCase()}-${width}.png`, import.meta.url).pathname, fullPage: true });
      await context.close();
      console.log(`PASS: ${width}px ${role}: ${scenarios.length} full playoffs, ${scenarios.length * 9} bracket screens, ${mutations.length} scores submitted through real UI`);
    }
  }
  console.log(`PASS: ${screens} bracket screens, ${scoresSubmitted} commissioner score submissions; all frontend requests fulfilled locally`);
} finally {
  await browser.close();
}
