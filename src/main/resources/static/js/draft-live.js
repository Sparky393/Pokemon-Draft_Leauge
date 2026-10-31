// Keep the same price/name object for the board card and its details popup.
function draftBoardPokemon(pick) {
  return { id: pick.pokemonId, name: pick.pokemonName, price: pick.price };
}

// Use the existing draft snapshot; this display never fetches or changes league data.
function draftTeamBudget(picks, teamId) {
  const selected = picks.filter(
    (pick) => String(pick.teamId) === String(teamId),
  );
  const slots = Math.max(0, 10 - selected.length);
  if (
    selected.some(
      (pick) => typeof pick.price !== "number" || !Number.isFinite(pick.price),
    )
  ) {
    return {
      text: "Budget unavailable",
      title: "A drafted Pokémon price is unavailable.",
    };
  }
  const remaining = 100 - selected.reduce((sum, pick) => sum + pick.price, 0);
  const average = slots ? Number((remaining / slots).toFixed(2)) : null;
  return {
    text: `$${remaining} left · ${slots ? `$${average}/slot` : "Roster full"}`,
    title: `$${remaining} remaining · ${slots} open slots${slots ? ` · $${average} average per remaining slot` : ""}`,
  };
}

function wireDraftBoardScrolling(board) {
  const scroller = board.querySelector(".draft-board-scroll");
  const position = board.querySelector(".draft-board-position");
  const arrows = [...board.querySelectorAll("[data-draft-scroll]")];
  if (!scroller || !position) return () => {};
  const update = () => {
    const max = Math.max(0, scroller.scrollWidth - scroller.clientWidth);
    position.max = String(max);
    position.value = String(scroller.scrollLeft);
    position.disabled = max === 0;
    position.setAttribute(
      "aria-valuetext",
      max
        ? `${Math.round((100 * scroller.scrollLeft) / max)}% across the draft board`
        : "All columns visible",
    );
    arrows.forEach((arrow) => {
      arrow.disabled =
        Number(arrow.dataset.draftScroll) < 0
          ? scroller.scrollLeft <= 0
          : scroller.scrollLeft >= max - 1;
    });
  };
  const move = () => {
    scroller.scrollLeft = Number(position.value);
    update();
  };
  position.addEventListener("input", move);
  scroller.addEventListener("scroll", update, { passive: true });
  window.addEventListener("resize", update);
  const clicks = arrows.map((arrow) => {
    const click = () =>
      scroller.scrollBy({
        left: Number(arrow.dataset.draftScroll) * scroller.clientWidth * 0.8,
        behavior: "smooth",
      });
    arrow.addEventListener("click", click);
    return click;
  });
  const observer =
    typeof ResizeObserver === "undefined" ? null : new ResizeObserver(update);
  observer?.observe(scroller);
  update();
  return () => {
    observer?.disconnect();
    position.removeEventListener("input", move);
    scroller.removeEventListener("scroll", update);
    window.removeEventListener("resize", update);
    arrows.forEach((arrow, i) => arrow.removeEventListener("click", clicks[i]));
  };
}

/* A live, snake-order draft board at the top of the Draft Room. */
(() => {
  let lastSignature = null;
  let refreshing = false;
  let draftOpen = true;
  let draftFinalized = false;
  let idleRevision = null;
  let disposeScrolling = () => {};
  const originalRenderDraft = window.renderDraft;

  function buildBoard(d) {
    const teams = [...state.teams].sort(
      (a, b) => a.draftPosition - b.draftPosition,
    );
    const count = teams.length;
    const rounds = 10;
    const picks = new Map(d.picks.map((p) => [Number(p.overallPick), p]));
    const currentPick = d.picks.length + 1;

    const headers = teams
      .map((team) => {
        const budget = draftTeamBudget(d.picks, team.id);
        return `<th scope="col" title="${esc(team.name)}">
          <span class="draft-team-position">#${team.draftPosition}</span>
          <span class="draft-team-name">${teamBrand(team, "xs")}</span>
          <span class="draft-team-budget" title="${esc(budget.title)}">${esc(budget.text)}</span>
        </th>`;
      })
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
          const pokemon = pick ? draftBoardPokemon(pick) : null;
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
      <div class="draft-scroll-controls">
        <button type="button" class="secondary" data-draft-scroll="-1" aria-label="Scroll draft board left">←</button>
        <input class="draft-board-position" type="range" min="0" max="0" value="0" step="1" aria-label="Scroll draft board horizontally">
        <button type="button" class="secondary" data-draft-scroll="1" aria-label="Scroll draft board right">→</button>
      </div>
      <p class="muted draft-scroll-hint">Drag the slider to see more teams. Team headers stay visible while you scroll through rounds.</p>
      <div class="draft-board-scroll" role="region" aria-label="Live snake draft board" tabindex="0">
        <table class="draft-snake-table">
          <thead><tr><th scope="col">Round</th>${headers}</tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    </div>`;
  }

  window.renderDraft = async function (snapshot) {
    const previous = document.querySelector(".draft-board-scroll");
    const scroll = {
      left: previous?.scrollLeft || 0,
      top: previous?.scrollTop || 0,
    };
    disposeScrolling();
    const d = snapshot || (await api("/api/draft"));
    await originalRenderDraft(d);
    draftOpen = d.settings.draftOpen;
    draftFinalized = d.settings.draftFinalized;
    idleRevision = d.idleRevision;
    lastSignature = JSON.stringify({
      pickCount: d.picks.length,
      lastPickId: d.lastPickId ?? null,
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
    if (board) {
      const scroller = board.querySelector(".draft-board-scroll");
      scroller.scrollLeft = scroll.left;
      scroller.scrollTop = scroll.top;
      disposeScrolling = wireDraftBoardScrolling(board);
    }
    board?.querySelectorAll(".draft-board-pokemon").forEach((card) => {
      card.addEventListener("click", () => {
        const pick = d.picks.find(
          (p) => String(p.pokemonId) === card.dataset.pokemonId,
        );
        if (pick) showPokemonDetails(draftBoardPokemon(pick));
      });
    });
    if (board) hydratePokemonCards(board);
    // Export is intentionally outside the scroll container, at the bottom of Draft Room.
    const exportFooter = document.createElement("div");
    exportFooter.className = "card";
    exportFooter.innerHTML = '<button id="download-full-draft-board" type="button" class="secondary">Download Full Draft Board</button><p class="muted small">Save every round and team in one PNG image on your device.</p>';
    page.appendChild(exportFooter);
    exportFooter.querySelector("button").addEventListener("click", async (event) => {
      const button = event.currentTarget;
      button.disabled = true;
      try {
        await window.downloadFullDraftBoard(d);
      } catch (error) {
        toast(error.message || "Could not download the draft board");
      } finally {
        button.disabled = false;
      }
    });
    return d;
  };

  async function checkDraft() {
    if (globalThis.leagueSession && !leagueSession.canPoll()) return;
    if (
      refreshing ||
      state.page !== "draft" ||
      !state.user ||
      document.hidden ||
      draftFinalized
    )
      return;
    refreshing = true;
    try {
      if (!draftOpen) {
        const status = await api("/api/draft/idle-status");
        if (
          status.revision !== idleRevision &&
          !document.querySelector(".pokemon-detail-overlay")
        )
          await window.renderDraft();
        return;
      }
      const d = await api("/api/draft/status");
      draftOpen = d.draftOpen;
      const next = JSON.stringify({
        pickCount: d.pickCount,
        lastPickId: d.lastPickId ?? null,
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

  setInterval(() => checkDraft(), 5000);
  window.addEventListener("focus", () => checkDraft());
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) checkDraft();
  });
})();
