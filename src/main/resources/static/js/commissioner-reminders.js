/* Actionable, read-only reminders in the existing Commissioner tab. */
(() => {
  const previous = renderManager;
  renderManager = async function () {
    await previous();
    if (state.user?.role !== "MANAGER") return;
    const root = document.querySelector("#page-manager .manager-grid");
    if (!root) return;
    const panel = document.createElement("section");
    panel.className = "card manager-reminders";
    panel.id = "commissioner-reminders";
    panel.innerHTML = `<div class="section-title"><div>
       <div class="eyebrow">Commissioner Only</div>
       <h2><span aria-hidden="true">🔔</span> Action Reminders</h2>
       <p class="muted">Separate reminders for locking scores, advancing weeks, lineup deadlines and each waiver period. No action runs automatically.</p>
       </div><button id="reminders-refresh" class="secondary" type="button">Refresh</button></div>
       <div id="reminders-list" aria-live="polite"><p class="muted">Checking league progress…</p></div>`;
    root.prepend(panel);
    const list = panel.querySelector("#reminders-list");
    async function update() {
      try {
        const notices = await api("/api/manager/reminders");
        list.replaceChildren();
        if (!notices.length) {
          const p = document.createElement("p");
          p.className = "muted";
          p.textContent = "All commissioner actions are up to date.";
          list.appendChild(p);
          return;
        }
        for (const n of notices) {
          const card = document.createElement("div");
          card.className = "manager-reminder-item";
          const title = document.createElement("strong");
          title.textContent = n.title;
          const message = document.createElement("p");
          message.className = "muted";
          message.textContent = n.message;
          const button = document.createElement("button");
          button.type = "button";
          button.className = "secondary";
          button.textContent =
            n.action === "lock"
              ? "See Week Locks"
              : n.action === "faab"
                ? "Go to FAAB Processing"
                : "Go to League Settings";
          button.onclick = () => {
            if (n.action === "faab")
              document
                .querySelector("#manager-faab-process")
                ?.scrollIntoView({ behavior: "smooth", block: "center" });
            else {
              document
                .querySelector(
                  n.action === "lock" ? "#manager-week-locks" : "#setting-week",
                )
                ?.scrollIntoView({ behavior: "smooth", block: "center" });
              if (n.action === "deadline")
                document.querySelector("#setting-deadline")?.focus();
              if (n.action === "week")
                document.querySelector("#setting-week")?.focus();
            }
          };
          card.append(title, message, button);
          list.appendChild(card);
        }
      } catch (e) {
        list.textContent = "Cannot load reminders: " + e.message;
      }
    }
    window.refreshCommissionerReminders = update;
    panel.querySelector("#reminders-refresh").onclick = update;
    await update();
  };
})();
