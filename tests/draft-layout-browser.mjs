/** Desktop/mobile layout checks against the standalone mock preview. No league server. */
import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const browser = await chromium.launch({ headless: true });
const preview = new URL("../target/draft-layout-preview.html", import.meta.url).href;
try {
  for (const width of [1440, 390]) {
    const context = await browser.newContext({ viewport: { width, height: 1000 } });
    const page = await context.newPage(),
      errors = [];
    page.on("pageerror",error => errors.push(error.message));
    await page.route("https://**/*",route =>
      route.fulfill({
        contentType: "image/svg+xml",
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160"><circle cx="80" cy="80" r="60" fill="#73d2ff"/></svg>'
      })
    );
    await page.goto(preview);
    await page.locator(".live-draft-board").waitFor();
    async function checkBudgetHeaders() {
      const headers = await page.evaluate(async () => {
        const draft = await api("/api/draft");
        return [...state.teams].sort((a, b) => a.draftPosition - b.draftPosition).map((team, i) => {
          const picks = draft.picks.filter((pick) => pick.teamId === team.id);
          const left = 100 - picks.reduce((sum, pick) => sum + pick.price, 0);
          const slots = 10 - picks.length;
          const label = document.querySelectorAll(".draft-team-budget")[i];
          const range = document.createRange();
          range.selectNodeContents(label);
          const box = label.getBoundingClientRect();
          return {
            actual: label.textContent,
            expected: `$${left} left · ${slots ? `$${Number((left / slots).toFixed(2))}/slot` : "Roster full"}`,
            fits: range.getBoundingClientRect().width <= box.width + 1 && label.scrollWidth <= label.clientWidth + 1
          };
        });
      });
      assert.equal(headers.length, 16);
      for (const header of headers) {
        assert.equal(header.actual, header.expected);
        assert.equal(header.fits, true, "Budget and average fit within the existing team column");
      }
    }
    await checkBudgetHeaders();
    await page.locator("#draft-type").selectOption("fire");
    await page.waitForFunction(
      () =>
        JSON.stringify(
          [...document.querySelectorAll("#draft-grid .pokemon-card")]
            .map(c => c.dataset.pokemonName)
            .sort()
        ) === JSON.stringify(["Incineroar", "Skeledirge"])
    );
    assert.equal(await page.locator("#draft-type option").count(), 19);
    const controlsFit = await page.locator(".pokemon-filters").evaluate(filters => {
      const box = filters.getBoundingClientRect();
      return [...filters.querySelectorAll("input,select,button")].every(control => {
        const rect = control.getBoundingClientRect();
        return rect.width === 0 || (rect.left >= box.left-1 && rect.right <= box.right + 1);
      });
    });
    assert.equal(controlsFit, true, "Type and other filters stay inside the filter bar");
    await page.locator("#draft-clear").click();
    assert.equal(await page.locator("#draft-type").inputValue(), "all");
    const originalWidth = (await page.locator(".content").boundingBox()).width;
    await page.locator("#sidebar-toggle").click();
    assert.equal(await page.locator("#league-sidebar").isVisible(), false);
    assert.equal(await page.locator("#sidebar-open").getAttribute("aria-expanded"), "false");
    if (width > 800)
      assert.ok((await page.locator(".content").boundingBox()).width > originalWidth + 200);
    await page.reload();
    await page.locator(".live-draft-board").waitFor();
    assert.equal(await page.locator("#league-sidebar").isVisible(), false);
    await page.locator("#sidebar-open").click();
    assert.equal(await page.locator("#league-sidebar").isVisible(), true);

    const garchomp = page.locator('.draft-board-pokemon[data-pokemon-name="Garchomp"]');
    await garchomp.click();
    const detail = page.locator(".pokemon-detail-overlay");
    await detail.locator("h2").waitFor();
    assert.equal(await detail.locator(".detail-badges span").first().innerText(), "$18");
    await detail.locator(".detail-close").click();

    const slider = page.locator(".draft-board-position"),
      scroller = page.locator(".draft-board-scroll");
    assert.equal(await slider.isVisible(), true);
    await slider.evaluate(input => {
      input.value = String(Number(input.max) / 2);
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    assert.ok(await scroller.evaluate(node => node.scrollLeft > 100));
    await scroller.evaluate(node => {
      node.scrollTop = 500;
    });
    await scroller.scrollIntoViewIfNeeded();
    const bounds = await scroller.boundingBox();
    const frozen = page.locator(".draft-snake-table thead");
    const frozenTop = (await frozen.boundingBox()).y;
    assert.ok(Math.abs(frozenTop - bounds.y) < 2, "The whole header row stays at the top of the board");
    for (const scrollTop of [200, 800, 1100]) {
      await scroller.evaluate((node, top) => { node.scrollTop = top; }, scrollTop);
      assert.ok(Math.abs((await frozen.boundingBox()).y - frozenTop) < 1,
        "The frozen row must not move as draft rounds scroll underneath");
    }
    const paint = await frozen.evaluate((head) => {
      const style = getComputedStyle(head);
      const cells = [...head.querySelectorAll("th")];
      const viewport = head.closest(".draft-board-scroll").getBoundingClientRect();
      const headerCell = cells.slice(1).find((cell) => {
        const box = cell.getBoundingClientRect();
        return Math.min(box.right, viewport.right) - Math.max(box.left, viewport.left + 70) > 12;
      });
      const box = headerCell?.getBoundingClientRect();
      const pointX = box && (Math.max(box.left, viewport.left + 70) + Math.min(box.right, viewport.right)) / 2;
      return {
        position: style.position,
        background: style.backgroundColor,
        solidCells: cells.every((cell) => getComputedStyle(cell).backgroundColor === "rgb(20, 43, 69)"),
        aboveCards: !!box && !!document.elementFromPoint(pointX, box.y + box.height / 2)?.closest("thead")
      };
    });
    assert.equal(paint.position, "sticky");
    assert.equal(paint.background, "rgb(20, 43, 69)", "Column gaps also have an opaque background");
    assert.equal(paint.solidCells, true, "Global translucent styles must not override the frozen cells");
    assert.equal(paint.aboveCards, true, "The frozen header stays above the scrolling cards");
    await scroller.evaluate(node => {
      node.scrollTop = 0;
      node.scrollLeft = 0;
    });
    const fit = await garchomp.evaluate(card => {
      const cell = card.closest("td").getBoundingClientRect(),
        box = card.getBoundingClientRect(),
        name = card.querySelector("strong");
      const range = document.createRange();
      range.selectNodeContents(name);
      return {
        inside: box.bottom <= cell.bottom + 1 && box.right <= cell.right + 1,
        lines: range.getClientRects().length,
        overflow: card.scrollHeight > card.clientHeight + 1
      };
    });
    assert.equal(fit.inside, true);
    assert.equal(fit.lines, 1, "Garchomp should fit on one line");
    assert.equal(fit.overflow, false);

    await page.locator('#draft-grid [data-pokemon-name="Meowscarada"]').click();
    await detail.locator(".detail-close").click();
    assert.match(await page.locator("#draft-action-selection").innerText(), /Meowscarada.*\$14/);
    assert.equal(await page.locator("#make-pick").isEnabled(), true);
    await page.locator("#make-pick").click();
    await page.locator('.draft-board-pokemon[data-pokemon-name="Meowscarada"]').waitFor();
    await checkBudgetHeaders();
    await page.locator("#preview-role").selectOption("GUEST");
    await page.waitForFunction(() => state.user.role === "GUEST");
    await page.locator('#draft-grid [data-pokemon-name="Corviknight"]').click();
    await detail.locator(".detail-close").click();
    assert.equal(await page.locator("#make-pick").isDisabled(), true);
    assert.equal(await page.locator("#manager-nav").isVisible(), false);
    await checkBudgetHeaders();
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
      false
    );
    assert.deepEqual(errors, []);
    await page.screenshot({
      path: new URL(`../target/draft-layout-${width}.png`, import.meta.url).pathname,
      fullPage: true
    });
    await context.close();
    console.log(
      `PASS: ${width}px collapse, selection, prices, solid frozen headers, persistent scrolling, card fit, budget headers and guest permissions`
    );
  }
} finally {
  await browser.close();
}
