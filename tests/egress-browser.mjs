/** Run only against a disposable local H2 app on port 18080. */
import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const origin = "http://127.0.0.1:18080";
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext();
const page = await context.newPage();
const errors = [];
const requests = [];
page.on("pageerror", (error) => errors.push(error.message));
page.on("request", (request) => {
  if (request.url().startsWith(origin + "/api/"))
    requests.push(new URL(request.url()).pathname);
});
await page.route("**/*", (route) =>
  route.request().url().startsWith(origin) ? route.continue() : route.abort(),
);
const count = (path) => requests.filter((request) => request === path).length;
async function navigate(name) {
  await page.locator(`[data-page="${name}"]`).first().click();
  await page.locator(`#page-${name}:not(.hidden)`).waitFor();
}
async function call(endpoint, method = "GET", body) {
  return page.evaluate(
    async ({ endpoint, method, body }) => {
      const response = await fetch(endpoint, {
        method,
        headers: {
          "Content-Type": "application/json",
          "X-Requested-With": "XMLHttpRequest",
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
      if (!response.ok)
        throw new Error(endpoint + " returned " + response.status);
      return response.status === 204 ? null : response.json();
    },
    { endpoint, method, body },
  );
}
try {
  await page.goto(origin, { waitUntil: "domcontentloaded" });
  await page.locator("#login-username").fill("manager");
  await page.locator("#login-password").fill("manager");
  await page.locator("#login-button").click();
  await page.locator("#page-home .page-hero").waitFor();
  assert.equal(count("/api/dashboard"), 1, "Login must fetch one dashboard");
  const beforeDraft = count("/api/draft");
  await navigate("draft");
  await page.locator(".draft-budget-panel").waitFor();
  assert.equal(
    count("/api/draft") - beforeDraft,
    1,
    "All draft components must share one fetch",
  );
  assert.equal(await page.locator(".live-draft-board").count(), 1);
  assert.equal(await page.locator(".draft-budget-panel").count(), 1);

  // A no-change poll must not fetch the full draft again.
  const full = count("/api/draft");
  const statusBefore = count("/api/draft/status");
  await page.waitForTimeout(5500);
  assert.ok(
    count("/api/draft/status") > statusBefore,
    "Active room must continue polling",
  );
  assert.equal(
    count("/api/draft"),
    full,
    "Unchanged status must not rerender/refetch",
  );

  // Pausing preserves the same five-second reopening response. The idle endpoint
  // reads only a tiny revision, and replaces full draft-status queries.
  await call("/api/manager/settings", "PUT", { draftOpen: false });
  await page.evaluate(() => renderDraft());
  const activeBefore = count("/api/draft/status");
  const idleBefore = count("/api/draft/idle-status");
  await page.waitForTimeout(5500);
  assert.equal(count("/api/draft/status"), activeBefore);
  assert.ok(
    count("/api/draft/idle-status") > idleBefore,
    "Paused draft uses a small reopening probe",
  );
  const reopenFull = count("/api/draft");
  await call("/api/manager/settings", "PUT", { draftOpen: true });
  await page.waitForFunction(() =>
    document.querySelector("#page-draft .draft-cell-current"),
  );
  await page.waitForTimeout(100);
  assert.equal(
    count("/api/draft") - reopenFull,
    1,
    "Reopening must rerender once",
  );

  // Finalization is covered end-to-end by the 160-pick H2 integration test.
  // Here a response fixture tests that the browser stops both polling loops.
  await page.route(origin + "/api/draft", async (route) => {
    const response = await route.fetch();
    const data = await response.json();
    data.settings.draftFinalized = true;
    data.settings.draftOpen = false;
    data.currentTeam = null;
    await route.fulfill({ response, json: data });
  });
  await page.evaluate(() => renderDraft());
  const finalizedActive = count("/api/draft/status"),
    finalizedIdle = count("/api/draft/idle-status");
  await page.waitForTimeout(5500);
  assert.equal(count("/api/draft/status"), finalizedActive);
  assert.equal(count("/api/draft/idle-status"), finalizedIdle);
  await page.unroute(origin + "/api/draft");

  await navigate("schedule");
  await page.locator("#schedule-week").waitFor();
  const scheduleReads = count("/api/flow/schedule");
  await page.locator("#schedule-week").selectOption("2");
  await page.waitForFunction(
    () => document.querySelectorAll(".season-week").length === 1,
  );
  assert.equal(
    count("/api/flow/schedule"),
    scheduleReads,
    "Filters reuse an already loaded full schedule",
  );
  await navigate("home");
  const filtered = page.waitForResponse((response) =>
    response.url().includes("/api/flow/schedule?week=2"),
  );
  await navigate("schedule");
  await filtered;
  await page.waitForFunction(
    () => document.querySelectorAll(".season-week").length === 1,
  );
  assert.equal(await page.locator(".season-week").count(), 1);
  await page.locator("#schedule-clear").click();
  await page.waitForFunction(
    () => document.querySelectorAll(".season-week").length === 16,
  );
  assert.equal(await page.locator(".season-week").count(), 16);
  assert.deepEqual(errors, []);
  console.log(
    "PASS browser: one dashboard, one draft snapshot, live/idle/finalized polling, reopening, schedule filters",
  );
} finally {
  await browser.close();
}
