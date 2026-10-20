/* A live, snake-order draft board at the top of the Draft Room. */
(() => {
  let lastSignature = null;
  let refreshing = false;
  const originalRenderDraft = window.renderDraft;

  function signature(d) {
    return JSON.stringify({
      picks: d.picks.map((p) => [p.overallPick, p.teamId, p.pokemonId]),
      currentTeam: d.currentTeam?.id ?? null,
      draftOpen: d.settings.draftOpen,
      draftFinalized: d.settings.draftFinalized,
      snakeDraft: d.settings.snakeDraft,
    });
  }

  function buildBoard(d) {
    const teams = [...state.teams].sort(
      (a, b) => a.draftPosition - b.draftPosition,
    );
    const count = teams.length;
    const rounds = 10;
    const picks = new Map(d.picks.map((p) => [Number(p.overallPick), p]));
    const pokemonById = new Map(d.available.map((p) => [p.id, p]));
    const currentPick = d.picks.length + 1;

    const headers = teams
      .map(
        (team) => `<th scope="col" title="${esc(team.name)}">
          <span class="draft-team-position">#${team.draftPosition}</span>
          <span class="draft-team-name">${teamBrand(team, "xs")}</span>
        </th>`,
      )
      .join("");

    const rows = Array.from({ length: rounds }, (_, index) => {
      const round = index + 1;
      const reversed = d.settings.snakeDraft && round % 2 === 0;
      const cells = teams
        .map((team, column) => {
          const position = reversed ? count - column : column + 1;
          const overall = index * count + position;
          const pick = picks.get(overall);
          const onClock =
            !pick &&
            overall === currentPick &&
            d.settings.draftOpen &&
            d.currentTeam;
          const className = pick
            ? "draft-cell-picked"
            : onClock
              ? "draft-cell-current"
              : "draft-cell-empty";
          const pokemon = pick
            ? {
                ...pokemonById.get(pick.pokemonId),
                id: pick.pokemonId,
                name: pick.pokemonName,
              }
            : null;
          const content = pokemon
            ? pokemonCard(pokemon, "draft-board-pokemon")
            : onClock
              ? '<span class="draft-on-clock">ON THE CLOCK</span>'
              : '<span class="draft-empty-slot">—</span>';

          return `<td class="${className}" title="Round ${round}, pick ${overall}: ${esc(team.name)}">
            <span class="draft-pick-number">Pick ${overall}</span>
            ${content}
          </td>`;
        })
        .join("");

      return `<tr>
        <th scope="row" class="draft-round-heading">R${round}<span>${reversed ? "←" : "→"}</span></th>
        ${cells}
      </tr>`;
    }).join("");

    return `<div class="card live-draft-board">
      <div class="section-title">
        <div>
          <h2>Live Draft Board</h2>
          <p class="muted">Teams are ordered by draft position. Arrows show the direction of each round.</p>
        </div>
        <span class="pool-count">${d.picks.length}/${count * rounds} picks</span>
      </div>
      <div class="draft-board-scroll" role="region" aria-label="Live snake draft board" tabindex="0">
        <table class="draft-snake-table">
          <thead><tr><th scope="col">Round</th>${headers}</tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    </div>`;
  }

  window.renderDraft = async function () {
    await originalRenderDraft();
    const d = await api("/api/draft");
    lastSignature = JSON.stringify({
      pickCount: d.picks.length,
      currentTeamId: d.currentTeam?.id ?? null,
      draftOpen: d.settings.draftOpen,
      draftFinalized: d.settings.draftFinalized,
      snakeDraft: d.settings.snakeDraft,
    });

    const page = document.querySelector("#page-draft");
    if (!page) return;

    // Remove the older chronological table and put the snake board above the picker.
    // The original chronological board is a direct child of the page.
    // Match its own heading, not a nested heading from another card.
    [...page.children]
      .filter((element) => element.classList.contains("card"))
      .filter(
        (card) =>
          card.querySelector(":scope > h2")?.textContent.trim() ===
          "Draft Board",
      )
      .forEach((card) => card.remove());

    const title = page.querySelector(".section-title");
    if (title) title.insertAdjacentHTML("afterend", buildBoard(d));
    else page.insertAdjacentHTML("afterbegin", buildBoard(d));

    const board = page.querySelector(".live-draft-board");
    board?.querySelectorAll(".draft-board-pokemon").forEach((card) => {
      card.addEventListener("click", () => {
        const pick = d.picks.find(
          (p) => String(p.pokemonId) === card.dataset.pokemonId,
        );
        if (pick)
          showPokemonDetails({ id: pick.pokemonId, name: pick.pokemonName });
      });
    });
    if (board) hydratePokemonCards(board);
  };

  async function checkDraft() {
    if (refreshing || state.page !== "draft" || !state.user || document.hidden)
      return;
    refreshing = true;
    try {
      const d = await api("/api/draft/status");
      const next = JSON.stringify({
        pickCount: d.pickCount,
        currentTeamId: d.currentTeamId,
        draftOpen: d.draftOpen,
        draftFinalized: d.draftFinalized,
        snakeDraft: d.snakeDraft,
      });
      if (lastSignature !== null && next !== lastSignature) {
        if (!document.querySelector(".pokemon-detail-overlay")) {
          await window.renderDraft();
        }
      } else if (lastSignature === null) {
        lastSignature = next;
      }
    } catch (e) {
      console.warn("Draft refresh failed:", e);
    } finally {
      refreshing = false;
    }
  }

  setInterval(checkDraft, 5000);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) checkDraft();
  });
})();
