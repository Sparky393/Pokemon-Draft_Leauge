/** Desktop/mobile UI regression. All URLs are fulfilled locally; no server or database. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const browser = await chromium.launch({ headless: true });
const root = new URL("../src/main/resources/static/", import.meta.url);
const origin = "http://league.test";
const teams = Array.from({ length: 16 }, (_, i) => ({
  id: i + 1, name: `Team ${i + 1}`, coachName: `Coach ${i + 1}`, division: i < 8 ? "A" : "B",
  draftPosition: i + 1, wins: 0, losses: 0, differential: 0, faBudget: 3,
}));
const pokemon = [{ id: 1, name: "Charizard", price: 10 }, { id: 2, name: "Raichu", price: 10 }];
const settings = { leagueName: "Test League", currentWeek: 1, regularSeasonWeeks: 13, draftOpen: true, snakeDraft: true, draftFinalized: false };

try {
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    const context = await browser.newContext({ viewport });
    const page = await context.newPage();
    const errors = [], calls = [];
    let user = null;
    page.on("pageerror", (error) => errors.push(error.message));
    await page.clock.install();
    await page.route("**/*", async (route) => {
      const req = route.request(), url = new URL(req.url());
      const json = (body, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
      if (url.origin !== origin) {
        if (url.hostname === "pokeapi.co") return json({
          sprites: { front_default: "http://league.test/fixture.png" }, moves: [],
          abilities: [{ ability: { name: "test-ability" } }],
          stats: [{ stat: { name: "hp" }, base_stat: 60 }],
        });
        return route.fulfill({ status: 200, contentType: "image/png", body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9YueWUkAAAAASUVORK5CYII=", "base64") });
      }
      const path = url.pathname;
      if (path.startsWith("/api/")) {
        calls.push(path);
        if (path === "/api/auth/guest") {
          user = { id: null, username: "Guest", role: "GUEST", teamId: null };
          return json(user);
        }
        if (path === "/api/auth/logout") { user = null; return json(null); }
        if (path === "/api/auth/me") return user ? json(user) : json({ message: "Please log in" }, 401);
        if (!user) return json({}, 401);
        switch (path) {
          case "/api/dashboard/view": return json({ user, teams, settings, currentDraftTeam: teams[0] });
          case "/api/teams": return json(teams);
          case "/api/settings": return json(settings);
          case "/api/rosters": return json(teams.map((team) => ({ team, pokemon: team.id === 1 ? pokemon.map((p) => ({ ...p, pokemonId: p.id })) : [] })));
          case "/api/rosters/1": return json([]);
          case "/api/draft": return json({ settings, currentTeam: teams[0], picks: [], available: pokemon, idleRevision: "fixture" });
          case "/api/draft/status": return json({ pickCount: 0, currentTeamId: 1, draftOpen: true, draftFinalized: false, snakeDraft: true, lastPickId: null });
          case "/api/standings": return json({ A: teams.slice(0, 8), B: teams.slice(8) });
          case "/api/flow/calendar": return json([{ week: 1, label: "Opening week", lineupRequired: true }]);
          case "/api/flow/schedule": return json([{ id: 1, week: 1, homeTeamId: 1, awayTeamId: 2, homeScore: 3, awayScore: 0, played: true, revealed: false }]);
          case "/api/weeks/locks": return json({});
          case "/api/playoffs": return json({ games: [], regularSeasonComplete: false, regularGamesRemaining: 80 });
          case "/api/transactions": return json([{ id: 1, type: "TRADE", description: "Public completed transaction", createdAt: "2026-10-24" }]);
          default: return json({ message: "Unexpected API request in guest UI fixture" }, 403);
        }
      }
      const asset = path === "/" ? "index.html" : path.slice(1);
      if (!/^(index\.html|(?:js|css)\/[a-z-]+\.(?:js|css))$/.test(asset)) return route.fulfill({ status: 204 });
      return route.fulfill({ body: readFileSync(new URL(asset, root)), contentType: asset.endsWith(".js") ? "text/javascript" : asset.endsWith(".css") ? "text/css" : "text/html" });
    });

    await page.goto(origin);
    const guest = page.locator("#guest-button"), login = page.locator("#login-button");
    assert.ok((await guest.boundingBox()).y > (await login.boundingBox()).y, "Guest button is below login");
    await guest.click();
    await page.locator("#app:not(.hidden)").waitFor();
    assert.match(await page.locator("#whoami").innerText(), /GUEST/);
    for (const name of ["lineup", "freeagency", "account", "manager"])
      assert.equal(await page.locator(`[data-page="${name}"]`).isVisible(), false);
    for (const name of ["rosters", "standings", "schedule", "playoffs", "transactions", "draft"]) {
      await page.locator(`[data-page="${name}"]`).click();
      await page.locator(`#page-${name}:not(.hidden) h1`).waitFor();
      assert.doesNotMatch(await page.locator(`#page-${name}`).innerText(), /Page error/);
    }
    assert.equal(await page.locator("#make-pick").isDisabled(), true);
    assert.equal(await page.locator(".season-score-controls:visible").count(), 0);
    for (const name of ["Charizard", "Raichu"]) {
      await page.locator("#draft-search").fill(`Mega ${name} Y`);
      const cards = page.locator("#draft-grid .pokemon-card");
      await cards.first().waitFor();
      assert.equal(await cards.count(), 1, "Form search returns one shared draft slot");
      await cards.first().click();
      const detail = page.locator(".pokemon-detail-overlay");
      await detail.locator("h2").waitFor();
      assert.equal(await detail.locator("[data-form]").count(), 1);
      await detail.locator('[data-form="mega"]').click();
      await detail.getByRole("heading", { name: `Mega ${name} X`, exact: true }).waitFor();
      await detail.locator('[data-form="mega"]').click();
      await detail.getByRole("heading", { name: `Mega ${name} Y`, exact: true }).waitFor();
      assert.match(await detail.innerText(), name === "Charizard" ? /Drought/ : /No Guard/);
      const image = await detail.locator(".detail-art img").getAttribute("src");
      assert.ok(image.endsWith(name === "Charizard" ? "10035.png" : "10305.png"));
      assert.ok(await detail.locator('[data-form="mega"]').isVisible(), "M control remains visible on mobile");
      await detail.locator('[data-form="mega"]').click();
      await detail.getByRole("heading", { name, exact: true }).waitFor();
      await detail.locator(".detail-close").click();
    }
    await page.locator('[data-page="home"]').click();
    await page.reload();
    await page.locator("#app:not(.hidden)").waitFor();
    await page.clock.fastForward(120000);
    assert.equal(calls.filter((p) => p === "/api/notifications").length, 0, "Guest does not poll notifications");
    assert.equal(calls.some((p) => ["/api/free-agency/bids", "/api/flow/lineup", "/api/auth/security"].includes(p)), false);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    assert.deepEqual(errors, []);
    await page.locator("#logout-button").click();
    await page.locator("#login-screen:not(.hidden)").waitFor();
    await context.close();
    console.log(`PASS: guest public navigation, no private reads/polls, refresh/logout, M → X → Y → base, ${viewport.width}px`);
  }
} finally {
  await browser.close();
}
