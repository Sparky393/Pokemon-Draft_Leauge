/** Run only against the disposable H2 portal at 127.0.0.1:18080. Never a deployed league. */
import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const origin = "http://127.0.0.1:18080";
const browser = await chromium.launch({ headless: true });
try {
  for (const width of [1440, 390]) {
    const context = await browser.newContext({
        viewport: { width, height: 1000 },
      }),
      page = await context.newPage();
    const requests = [];
    page.on("request", (request) => {
      if (request.url().startsWith(origin + "/api/"))
        requests.push(new URL(request.url()).pathname);
    });
    await page.route("**/*", (route) =>
      route.request().url().startsWith(origin)
        ? route.continue()
        : route.abort(),
    );
    await page.clock.install();
    await page.goto(origin);
    async function login() {
      await page.locator("#login-username").fill("coach1");
      await page.locator("#login-password").fill("password");
      await page.locator("#login-button").click();
      await page.locator("#page-home h1").waitFor();
    }
    await login();
    const before = await page.evaluate(async () => ({
      draft: (await api("/api/draft")).picks,
      teams: await api("/api/teams"),
    }));

    // Simulate a hidden document without requiring operating-system window switching.
    await page.evaluate(() => {
      window.idleTestHidden = true;
      Object.defineProperty(document, "hidden", {
        configurable: true,
        get: () => window.idleTestHidden,
      });
      document.dispatchEvent(new Event("visibilitychange"));
    });
    const hiddenCount = requests.length;
    await page.clock.fastForward(60000);
    assert.equal(requests.length, hiddenCount, "Hidden tabs do not poll");
    await page.evaluate(() => {
      window.idleTestHidden = false;
      document.dispatchEvent(new Event("visibilitychange"));
    });
    await page.waitForFunction(
      () => document.querySelector("#app").inert === false,
    );
    await page.locator("#whoami").click();
    await page.clock.fastForward(120000);
    assert.equal(await page.locator("#app").isVisible(), true);
    await page.keyboard.press("Tab"); // Real input, unlike a polling response.
    await page.clock.fastForward(120000);
    assert.equal(
      await page.locator("#app").isVisible(),
      true,
      "Real input extends the idle deadline",
    );
    await page.clock.fastForward(61000);
    await page.locator("#login-screen:not(.hidden)").waitFor();
    assert.match(
      await page.locator("#session-idle-message").innerText(),
      /3 minutes/,
    );
    assert.equal(
      requests.filter((path) => path === "/api/auth/logout").length,
      1,
    );
    const stoppedCount = requests.length;
    await page.clock.fastForward(240000);
    assert.equal(
      requests.length,
      stoppedCount,
      "No automatic API calls after timeout",
    );
    // A deliberate raw probe verifies the real server session was invalidated.
    assert.equal(
      await page.evaluate(async () => {
        const response = await fetch("/api/auth/me");
        return response.status;
      }),
      401,
    );
    await login();
    const after = await page.evaluate(async () => ({
      draft: (await api("/api/draft")).picks,
      teams: await api("/api/teams"),
    }));
    assert.deepEqual(
      after,
      before,
      "Timeout/login must not change saved league data",
    );
    await context.close();
    console.log(
      `PASS: ${width}px inactivity logout, hidden polling pause, return refresh, real-input extension, server invalidation, re-login and data preservation`,
    );
  }
} finally {
  await browser.close();
}
