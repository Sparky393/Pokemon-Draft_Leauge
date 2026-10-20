import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";

const url = "http://127.0.0.1:18080/";
const browser = await chromium.launch({ headless: true });
await mkdir("tests/screenshots", { recursive: true });

async function checkViewport(width, height, role) {
  const context = await browser.newContext({
    viewport: { width, height }, isMobile: width <= 500,
    deviceScaleFactor: 1, hasTouch: width <= 500
  });
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  page.on("pageerror", error => { throw error; });
  try {
    await page.route("https://pokeapi.co/**", route => route.abort());
    await page.goto(url, { waitUntil: "domcontentloaded" });
    await page.locator("#login-username").fill(role === "manager" ? "manager" : "coach1");
    await page.locator("#login-password").fill(role === "manager" ? "manager" : "password");
    await page.locator("#login-button").click();
    await page.locator("#app:not(.hidden)").waitFor();
    const pages = role === "manager"
      ? ["home","draft","rosters","standings","schedule","playoffs","lineup","manager","account"]
      : ["home","draft","rosters","standings","schedule","playoffs","lineup","freeagency","account"];
    if (role === "manager") {
      if (await page.locator('.sidebar nav button[data-page="freeagency"]').isVisible())
        throw new Error("Commissioner must not see the Free Agency tab");
    } else {
      if (!await page.locator('.sidebar nav button[data-page="freeagency"]').isVisible())
        throw new Error("Coaches must retain the Free Agency tab");
    }
    for (const name of pages) {
      const selector = name === "account" ? '.sidebar > button[data-page="account"]'
        : `.sidebar nav button[data-page="${name}"]`;
      await page.locator(selector).click();
      await page.waitForTimeout(450);
      await page.locator(`#page-${name}:not(.hidden)`).waitFor();
      // Error rendering still keeps the page visible, so detect the error card.
      const pageError = await page.locator(`#page-${name} h2`).allTextContents();
      if (name === "manager" && role === "manager") {
        await page.locator("#manager-process-faab").waitFor();
        if (await page.locator("#fill-test-rosters").count())
          throw new Error("Temporary Fill All Rosters control must be removed");
        if (await page.locator("#setting-snake").count())
          throw new Error("Commissioner must not see a snake draft toggle");
        if (await page.locator("#page-manager").getByText("All FAAB Bids").count())
          throw new Error("Blind bid table must not appear to commissioner");
      }
      if (pageError.includes("Page error"))
        throw new Error(`Page ${name} rendered an API exception`);
      if (width <= 800) {
        const { total, screen } = await page.evaluate(() => ({
          total: document.documentElement.scrollWidth, screen: window.innerWidth
        }));
        if (total > screen + 2)
          throw new Error(`Page ${name} overflows mobile viewport: ${total}px > ${screen}px`);
      }
    }
    // Exercise the real backend against this throwaway H2 test league.
    // These actions NEVER connect to Neon.
    async function call(method, endpoint, body) {
      return page.evaluate(async ({method,endpoint,body}) => {
        const response = await fetch(endpoint,{
          method,
          headers:{"Content-Type":"application/json","X-Requested-With":"XMLHttpRequest"},
          ...(body === undefined ? {} : {body:JSON.stringify(body)})
        });
        const text = await response.text();
        let value;
        try { value=JSON.parse(text); } catch { value=text; }
        return {status:response.status,value};
      },{method,endpoint,body});
    }
    if (width === 390 && role === "coach") {
      const draft = await call("GET","/api/draft");
      if (draft.status !== 200 || draft.value.picks.length !== 0)
        throw new Error("Fresh H2 draft should begin with zero picks");
      const candidate = draft.value.available.find(p => p.price <= 100);
      const selected = await call("POST","/api/draft/pick",{pokemonId:candidate.id});
      if (selected.status !== 200)
        throw new Error("First coach draft pick failed: " + JSON.stringify(selected));
      const saved = await call("GET","/api/draft");
      if (saved.value.picks.length !== 1 || saved.value.picks[0].pokemonId !== candidate.id)
        throw new Error("Draft pick was not persisted on re-read");
      const duplicate = await call("POST","/api/draft/pick",{pokemonId:candidate.id});
      if (duplicate.status < 400) throw new Error("Duplicate/out-of-turn draft pick was accepted");
      const beforeWeek4 = await call("POST","/api/free-agency/bids",
        {pokemonWantedId:candidate.id,pokemonDropId:candidate.id,amount:1});
      if (beforeWeek4.status < 400)
        throw new Error("FAAB bids must remain blocked before Week 4");
    }
    if (width === 320 && role === "manager") {
      const fullSchedule = await call("GET","/api/schedule");
      const week1 = fullSchedule.value.filter(game => game.week === 1);
      if (week1.length !== 8) throw new Error("Expected all eight official Week 1 matchups");
      for (const game of week1) {
        const result = await call("POST",`/api/manager/results/${game.id}`,
          {homeScore:2,awayScore:1});
        if (result.status !== 200) throw new Error("Commissioner could not score a game");
      }
      const week2 = fullSchedule.value.find(game => game.week === 2);
      if (!week2) throw new Error("Missing Week 2 fixture");
      const week2Saved = await call("POST",`/api/manager/results/${week2.id}`,
        {homeScore:0,awayScore:3});
      if (week2Saved.status !== 200)
        throw new Error("Week 2 rejected valid 0-3 score: " + JSON.stringify(week2Saved));
      const persisted = (await call("GET","/api/schedule")).value.find(game => game.id === week2.id);
      if (!persisted.played || persisted.homeScore !== 0 || persisted.awayScore !== 3)
        throw new Error("Week 2 score did not persist");
      const invalid = await call("POST",`/api/manager/results/${week2.id}`,
        {homeScore:3,awayScore:1});
      if (invalid.status < 400) throw new Error("Week 2 incorrectly accepted four games");
      const stillSaved = (await call("GET","/api/schedule")).value.find(game => game.id === week2.id);
      if (stillSaved.homeScore !== 0 || stillSaved.awayScore !== 3)
        throw new Error("Invalid score overwrote Week 2 result");
      await page.locator('.sidebar nav button[data-page="schedule"]').click();
      await page.locator("#schedule-week").selectOption("2");
      const maxScore = await page.locator('.score-home').first().getAttribute("max");
      if (maxScore !== "3") throw new Error("Regular-season score field must allow 3 wins");
            const lock = await call("PUT","/api/manager/weeks/1/lock",{locked:true});
      if (lock.status !== 200 || lock.value["1"] !== true)
        throw new Error("Commissioner failed to lock a completed week");
      const rejected = await call("DELETE",`/api/manager/results/${week1[0].id}`);
      if (rejected.status !== 409)
        throw new Error("A locked week allowed score changes");
      const unlock = await call("PUT","/api/manager/weeks/1/lock",{locked:false});
      if (unlock.status !== 200 || unlock.value["1"] !== false)
        throw new Error("Commissioner failed to unlock Week 1");
      const reset = await call("POST","/api/manager/league-reset",{confirmation:"NO"});
      if (reset.status < 400) throw new Error("League reset accepted an invalid confirmation");
      const after = await call("GET","/api/draft");
      if (after.value.picks.length !== 1)
        throw new Error("A rejected reset unexpectedly changed the saved draft");
    }
    if (width <= 800) {
      const style = await page.locator(".sidebar nav").evaluate(el => getComputedStyle(el));
      if (style.overflowX !== "auto")
        throw new Error("Mobile navigation must scroll horizontally");
    }
    await page.screenshot({ path: `tests/screenshots/${role}-${width}.png`, fullPage: true });
    process.stdout.write(`PASS ${role}, ${width}x${height}, ${pages.length} pages\n`);
  } catch (error) {
    await page.screenshot({path: `tests/screenshots/failed-${role}-${width}.png`,fullPage:true}).catch(()=>{});
    throw error;
  } finally {
    await context.close();
  }
}

try {
  await checkViewport(390, 844, "coach");
  await checkViewport(320, 700, "manager");
  await checkViewport(768, 900, "manager");
  await checkViewport(1440, 900, "coach");
} finally {
  await browser.close();
}
