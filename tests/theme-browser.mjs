// Run against a local sample league; checks both appearances and saved preference.
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || "playwright"
);
const baseUrl = process.env.BASE_URL || "http://127.0.0.1:18081/";
const browser = await chromium.launch({
  headless: true,
  ...(process.env.CHROME_PATH
    ? { executablePath: process.env.CHROME_PATH }
    : {}),
});
const audit = () => {
  const rgb = (s) => [...(s.match(/[\d.]+/g) || [])].map(Number);
  const lum = (c) =>
    c
      .slice(0, 3)
      .map((v) => {
        v /= 255;
        return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
      })
      .reduce((v, n, i) => v + n * [0.2126, 0.7152, 0.0722][i], 0);
  const fails = [];
  for (const el of document.querySelectorAll("#app *,#login-screen *")) {
    if (
      !el.getClientRects().length ||
      ![...el.childNodes].some(
        (n) => n.nodeType === 3 && n.textContent.trim(),
      ) ||
      el.closest(".team-avatar") ||
      el.disabled
    )
      continue;
    const s = getComputedStyle(el);
    let bg;
    let p = el;
    while (p) {
      const ps = getComputedStyle(p);
      const c = rgb(ps.backgroundColor);
      if (ps.backgroundImage !== "none") {
        const m = ps.backgroundImage.match(/rgb\([^)]+\)/);
        if (m) {
          bg = rgb(m[0]);
          break;
        }
      }
      if (c.length >= 3 && (c[3] ?? 1) === 1) {
        bg = c;
        break;
      }
      p = p.parentElement;
    }
    if (!bg) continue;
    const c = rgb(s.color),
      a = lum(c),
      b = lum(bg),
      ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    const large =
      parseFloat(s.fontSize) >= 24 ||
      (parseFloat(s.fontSize) >= 18.66 && parseFloat(s.fontWeight) >= 700);
    if (ratio < (large ? 3 : 4.5))
      fails.push({
        tag: el.tagName,
        cls: el.className,
        text: el.textContent.trim().slice(0, 60),
        ratio: ratio.toFixed(2),
        fg: s.color,
        bg,
      });
  }
  return fails;
};
for (const width of [1440, 390]) {
  for (const role of ["manager", "coach1"]) {
    const context = await browser.newContext({
      viewport: { width, height: 1000 },
      colorScheme: "light",
    });
    const page = await context.newPage();
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    await page.goto(baseUrl);
    await page.locator("#login-username").fill(role);
    await page
      .locator("#login-password")
      .fill(role === "manager" ? "manager" : "password");
    await page.click("#login-button");
    await page.locator("#app:not(.hidden)").waitFor();
    await page.evaluate(async () => {
      await pendingPageLoad;
    });
    for (const theme of ["light", "dark"]) {
      if ((await page.locator("html").getAttribute("data-theme")) !== theme)
        await page.locator(".sidebar .theme-toggle").click();
      for (const name of [
        "home",
        "draft",
        "rosters",
        "standings",
        "schedule",
        "playoffs",
        "lineup",
        role === "manager" ? "manager" : "freeagency",
        "transactions",
        "account",
      ]) {
        await page
          .locator(`.sidebar button[data-page="${name}"]`)
          .first()
          .click();
        await page.locator(`#page-${name}:not(.hidden)`).waitFor();
        await page.evaluate(async () => {
          await pendingPageLoad;
        });
        if (
          await page
            .locator(`#page-${name}`)
            .getByText("Page error", { exact: true })
            .count()
        )
          throw Error(`Page failed: ${name}`);
        // Let existing button/page transitions settle before sampling computed colors.
        await page.waitForTimeout(300);
        const failures = await page.evaluate(audit);
        if (failures.length) {
          console.log(JSON.stringify({ width, role, theme, name, failures }));
          throw Error("Text contrast below target");
        }
        if (
          await page.evaluate(
            () => document.documentElement.scrollWidth > innerWidth + 2,
          )
        )
          throw Error(`overflow ${width} ${theme} ${name}`);
      }
    }
    await page.reload();
    if ((await page.locator("html").getAttribute("data-theme")) !== "dark")
      throw Error("Theme did not persist");
    await page.locator(".sidebar .theme-toggle").click();
    if ((await page.locator("html").getAttribute("data-theme")) !== "light")
      throw Error("Toggle back failed");
    if (errors.length) throw Error(errors.join("\n"));
    console.log(JSON.stringify({ width, role, errors, persistence: "passed" }));
    await context.close();
  }
}
await browser.close();
