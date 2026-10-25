/* Navigation preference is browser-only; expanding/collapsing never loads league data. */
(() => {
  const app = document.querySelector("#app");
  const close = document.querySelector("#sidebar-toggle");
  const open = document.querySelector("#sidebar-open");
  if (!app || !close || !open) return;
  const key = "league-navigation-collapsed";
  function setCollapsed(collapsed, moveFocus = false) {
    app.classList.toggle("sidebar-collapsed", collapsed);
    open.hidden = !collapsed;
    close.setAttribute("aria-expanded", String(!collapsed));
    open.setAttribute("aria-expanded", String(!collapsed));
    try { localStorage.setItem(key, String(collapsed)); } catch {}
    if (moveFocus) (collapsed ? open : close).focus();
  }
  let collapsed = false;
  try { collapsed = localStorage.getItem(key) === "true"; } catch {}
  setCollapsed(collapsed);
  close.addEventListener("click", () => setCollapsed(true, true));
  open.addEventListener("click", () => setCollapsed(false, true));
})();
