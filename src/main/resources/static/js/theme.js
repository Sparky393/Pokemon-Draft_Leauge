/* Apply before paint; appearance stays in this browser and never calls the API. */
(() => {
  const key = "league-color-theme";
  const system = window.matchMedia("(prefers-color-scheme: dark)");
  let preference;
  try {
    preference = localStorage.getItem(key);
  } catch {}
  const root = document.documentElement;
  function apply(theme) {
    root.dataset.theme = theme;
    document.querySelectorAll(".theme-toggle").forEach((button) => {
      const dark = theme === "dark";
      button.textContent = dark ? "☀ Light mode" : "☾ Dark mode";
      button.setAttribute("aria-pressed", String(dark));
      button.setAttribute(
        "aria-label",
        dark ? "Switch to light mode" : "Switch to dark mode",
      );
    });
  }
  apply(
    preference === "dark" || preference === "light"
      ? preference
      : system.matches
        ? "dark"
        : "light",
  );
  system.addEventListener("change", (event) => {
    if (preference !== "light" && preference !== "dark")
      apply(event.matches ? "dark" : "light");
  });
  document.addEventListener("DOMContentLoaded", () => {
    apply(root.dataset.theme);
    document.querySelectorAll(".theme-toggle").forEach((button) => {
      button.addEventListener("click", () => {
        preference = root.dataset.theme === "dark" ? "light" : "dark";
        try {
          localStorage.setItem(key, preference);
        } catch {}
        apply(preference);
      });
    });
  });
})();
