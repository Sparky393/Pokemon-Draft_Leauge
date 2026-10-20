/* Commissioner tools. Every destructive action requires explicit confirmation. */
(() => {
  async function addCommissionerTools() {
    if (!state?.user || state.user.role !== "MANAGER") return;
    const page = document.querySelector("#page-manager");
    if (!page || document.querySelector("#testing-roster-tools")) return;

    const section = document.createElement("section");
    section.id = "testing-roster-tools";
    section.className = "card glass-panel";
    section.innerHTML = `
      <div class="section-title"><div>
        <div class="eyebrow">Commissioner Only</div>
        <h2>League Season Controls</h2>
        <p class="muted">Finalize completed weeks or reset to a fresh, pre-draft league.</p>
      </div></div>
      <div class="manager-season-section">
        <h3>Lock Weekly Results</h3>
        <p class="muted">Each week can be locked after every game has a recorded score. A locked week's scores cannot be changed or cleared, including via the API. Unlock it here to make corrections.</p>
        <div id="manager-week-locks" class="manager-week-list"><p class="muted">Loading weeks…</p></div>
      </div>
      <div class="manager-season-section manager-reset-section">
        <h3>Reset Entire League — Before the Draft</h3>
        <p>Deletes draft picks, roster ownership, trades, bids, transaction history, lineups,
           played scores, standings, playoff progress, and week locks. Restores all FAAB budgets
           to $3 and returns to Week 1.</p>
        <p><strong>Preserves:</strong> every Pokémon and its price, team names, coaches, passwords,
           divisions, draft order, team logos, league name, draft format, and official schedule.</p>
        <div id="league-reset-preview" class="manager-reset-stats muted">Loading reset preview…</div>
        <label class="manager-reset-confirm">Enter the reset password to enable reset
          <input id="league-reset-phrase" type="password" autocomplete="off" spellcheck="false"
            placeholder="League reset password">
        </label>
        <button id="reset-league-now" class="danger" type="button" disabled>Reset Entire League</button>
        <p class="muted small">Permanent without a separate database backup. Nothing is reset by deploying code or viewing this page.</p>
      </div>`;
    page.prepend(section);

    const $tool = (selector) => section.querySelector(selector);
    const phrase = $tool("#league-reset-phrase");
    const resetButton = $tool("#reset-league-now");
    let previewReady = false;
    phrase.addEventListener("input", () => {
      resetButton.disabled = !previewReady || !phrase.value.trim();
    });

    async function loadData() {
      try {
        const [locks, games, preview] = await Promise.all([
          api("/api/weeks/locks"),
          api("/api/schedule"),
          api("/api/manager/league-reset/preview"),
        ]);
        previewReady = true;
        resetButton.disabled = !phrase.value.trim();
        $tool("#league-reset-preview").innerHTML = `
          <span><strong>${preview.draftPicks}</strong> picks</span>
          <span><strong>${preview.rosterEntries}</strong> roster entries</span>
          <span><strong>${preview.bids}</strong> bids</span>
          <span><strong>${preview.trades}</strong> trades</span>
          <span><strong>${preview.transactions}</strong> transactions</span>
          <span><strong>${preview.weeklyLineups}</strong> lineups</span>
          <span><strong>${preview.playedGames}</strong> scored games</span>`;
        const lockedWeeks = [];
        for (let week = 1; week <= 16; week++) {
          const weekGames = games.filter((g) => g.week === week);
          const expected = [4, 8, 9].includes(week)
            ? 0
            : week <= 13
              ? 8
              : week === 14
                ? 4
                : week === 15
                  ? 2
                  : 1;
          const completed = weekGames.filter(
            (g) => g.played && g.homeScore != null && g.awayScore != null,
          ).length;
          const locked = locks[String(week)] === true;
          lockedWeeks.push(`<div class="manager-week-item">
            <div class="manager-week-label"><strong>Week ${week}</strong>
              <small>${expected === 0 ? "No games" : `${completed} of ${expected} results`} · ${locked ? "Locked" : "Editable"}</small>
            </div>
            <button type="button" class="${locked ? "secondary" : "primary"} manager-lock-button"
              data-week="${week}" data-locked="${locked}"
              ${!locked && (expected === 0 || completed !== expected || weekGames.length !== expected) ? "disabled" : ""}>
              ${locked ? "Unlock" : "Lock"}
            </button></div>`);
        }
        $tool("#manager-week-locks").innerHTML = lockedWeeks.join("");
        section.querySelectorAll(".manager-lock-button").forEach((button) => {
          button.onclick = async () => {
            const week = Number(button.dataset.week);
            const lock = button.dataset.locked !== "true";
            if (!confirm(`${lock ? "Lock" : "Unlock"} Week ${week} results?`))
              return;
            button.disabled = true;
            try {
              await api(`/api/manager/weeks/${week}/lock`, {
                method: "PUT",
                body: { locked: lock },
              });
              toast(`Week ${week} ${lock ? "locked" : "unlocked"}`);
              await loadData();
              window.refreshCommissionerReminders?.();
            } catch (error) {
              toast(error.message, true);
              button.disabled = false;
            }
          };
        });
      } catch (error) {
        $tool("#manager-week-locks").textContent =
          "Could not load controls: " + error.message;
        $tool("#league-reset-preview").textContent = "Could not preview reset.";
        toast(error.message, true);
      }
    }

    resetButton.onclick = async () => {
      if (!phrase.value.trim() || !previewReady) {
        toast("Enter the league reset password first.", true);
        return;
      }
      if (
        !confirm(
          "ARE YOU SURE? This permanently resets every draft pick, roster, bid, trade, lineup, recorded score, and transaction. All Pokémon become available again. Team identities, logos, divisions, Pokémon prices and published fixtures stay unchanged. Continue?",
        )
      )
        return;
      if (
        !confirm(
          "FINAL CONFIRMATION: Reset the live Neon league to its PRE-DRAFT state now? This cannot be undone without a database backup.",
        )
      )
        return;
      resetButton.disabled = true;
      try {
        await api("/api/manager/league-reset", {
          method: "POST",
          body: { password: phrase.value },
        });
        toast("League reset to its pre-draft state");
        await refreshBase();
        await renderManager();
      } catch (error) {
        toast(error.message, true);
        resetButton.disabled = false;
      } finally {
        phrase.value = "";
        resetButton.disabled = true;
      }
    };
    await loadData();
  }

  const managerNav = document.querySelector("#manager-nav");
  if (managerNav)
    managerNav.addEventListener("click", () =>
      setTimeout(addCommissionerTools, 0),
    );

  const managerPage = document.querySelector("#page-manager");
  if (managerPage) {
    new MutationObserver(() => {
      if (!managerPage.classList.contains("hidden")) addCommissionerTools();
    }).observe(managerPage, { childList: true });
  }
})();
