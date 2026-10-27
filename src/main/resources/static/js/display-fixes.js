/* Visual improvements for lineups, schedule reveals, and roster values. Loaded last. */
(() => {
  function displayCard(p, selected = false, selectable = false) {
    const id = p.pokemonId ?? p.id;
    return `<button type="button" class="pokemon-card display-pokemon-card ${selectable ? "display-selectable" : ""} ${selected ? "selected" : ""}" data-pokemon-id="${id}"><div class="pokemon-card-art" data-poke-name="${esc(p.name)}"></div><div class="pokemon-card-body"><strong>${esc(p.name)}</strong></div></button>`;
  }

  function bindDisplayCards(root, source) {
    if (!root) return;
    hydratePokemonCards(root);
    root.querySelectorAll(".display-pokemon-card").forEach((card) =>
      card.addEventListener("click", () => {
        const id = Number(card.dataset.pokemonId);
        const p = source.find((x) => Number(x.pokemonId ?? x.id) === id);
        if (p) showPokemonDetails(p);
      }),
    );
  }

  window.renderRosters = async function () {
    const data = await api("/api/rosters");
    $("#page-rosters").innerHTML =
      `<div class="section-title"><div><h1>Rosters</h1><p class="muted">10 Pokémon maximum · $100 roster value cap.</p></div></div><div class="grid roster-team-grid">${data
        .map((r) => {
          const value = r.pokemon.reduce(
            (sum, p) => sum + Number(p.price || 0),
            0,
          );
          return `<div class="card team-card roster-value-card"><div class="section-title"><div><h3>${esc(r.team.name)}</h3><p>${esc(r.team.coachName)} · Division ${r.team.division}</p></div><div class="roster-badges"><span class="pool-count">${r.pokemon.length}/10</span><span class="roster-value ${value > 100 ? "over" : ""}">$${value}/$100</span></div></div><div class="roster-mini-grid">${r.pokemon.map((p) => displayCard(p)).join("") || '<p class="muted">No Pokémon rostered.</p>'}</div></div>`;
        })
        .join("")}</div>`;
    const all = data.flatMap((r) => r.pokemon);
    bindDisplayCards($("#page-rosters"), all);
  };

  window.renderLineup = async function () {
    if (state.user.role === "MANAGER") {
      $("#page-lineup").innerHTML =
        '<h1>Weekly Lineup</h1><div class="card"><p>Coach lineups appear on Schedule as soon as both teams submit.</p></div>';
      return;
    }

    const d = await api("/api/flow/lineup");
    const selected = new Set(d.selectedPokemonIds || []);
    $("#page-lineup").innerHTML =
      `<div class="section-title"><div><h1>Weekly Lineup</h1><p class="muted">Week ${d.week} · choose exactly 6 Pokémon.</p></div><span id="lineup-count" class="pool-count">${selected.size}/6 selected</span></div><div class="card"><div class="lineup-heading"><div><h2>Your Roster</h2><p class="muted">Click a card to select it. Use the stats button area by clicking the Pokémon name/art again after selection.</p></div><p class="muted">${d.locked ? "Both teams submitted · lineup locked" : `Deadline: ${d.deadline ? new Date(d.deadline).toLocaleString() : "No deadline set"}`}</p></div><div id="lineup-grid" class="pokemon-card-grid lineup-draft-grid">${d.roster.map((p) => displayCard(p, selected.has(p.pokemonId), true)).join("")}</div><div class="selection-footer"><span id="lineup-message">${d.locked ? "Your six are locked and revealed on Schedule." : "Select exactly six Pokémon."}</span><button id="submit-lineup" class="primary" ${d.locked ? "disabled" : ""}>Submit Team of 6</button></div></div>`;

    const grid = $("#lineup-grid");
    hydratePokemonCards(grid);

    grid.querySelectorAll(".display-selectable").forEach((c) => {
      c.onclick = () => {
        if (d.locked) {
          const p = d.roster.find(
            (x) => x.pokemonId === Number(c.dataset.pokemonId),
          );
          if (p) showPokemonDetails(p);
          return;
        }

        const id = Number(c.dataset.pokemonId);
        if (selected.has(id)) {
          selected.delete(id);
          c.classList.remove("selected");
        } else {
          if (selected.size >= 6)
            return toast("You can only select 6 Pokémon", true);
          selected.add(id);
          c.classList.add("selected");
        }

        $("#lineup-count").textContent = `${selected.size}/6 selected`;
        $("#lineup-message").textContent =
          selected.size === 6
            ? "Ready to submit."
            : "Select exactly six Pokémon.";
      };
    });

    grid.querySelectorAll(".display-selectable").forEach((c) =>
      c.addEventListener("dblclick", (e) => {
        e.preventDefault();
        const p = d.roster.find(
          (x) => x.pokemonId === Number(c.dataset.pokemonId),
        );
        if (p) showPokemonDetails(p);
      }),
    );

    $("#submit-lineup").onclick = async () => {
      if (selected.size !== 6) return toast("Select exactly 6 Pokémon", true);
      try {
        await api("/api/flow/lineup", {
          method: "POST",
          body: { pokemonIds: [...selected] },
        });
        toast("Weekly lineup submitted");
        await renderLineup();
      } catch (e) {
        toast(e.message, true);
      }
    };
  };

  window.renderSchedule = async function () {
    const data = await api("/api/flow/schedule");
    const by = {};
    data.forEach((m) => (by[m.week] ??= []).push(m));

    const lineup = (team, list, automatic) =>
      `<div class="schedule-team-lineup"><div class="schedule-team-title"><h4>${esc(team)}</h4>${automatic ? '<span class="pill">Auto</span>' : ""}</div><div class="pokemon-card-grid schedule-six-grid">${(list || []).map((p) => displayCard(p)).join("")}</div></div>`;
    $("#page-schedule").innerHTML =
      `<div class="section-title"><div><h1>Schedule / Results</h1><p class="muted">Both submitted lineups reveal together. Click any revealed Pokémon to view its stats.</p></div></div>${Object.keys(
        by,
      )
        .map(
          (w) =>
            `<div class="card week schedule-week"><h2>Week ${w}</h2>${by[w].map((m) => `<div class="matchup matchup-with-lineups schedule-matchup"><div class="matchup-score"><strong>${esc(m.homeTeam)}</strong><span>${m.played ? m.homeScore : "—"}</span><strong>${esc(m.awayTeam)}</strong><span>${m.played ? m.awayScore : "—"}</span></div>${m.revealed ? `<div class="schedule-reveal-grid">${lineup(m.homeTeam, m.homeLineup, m.homeAutomatic)}${lineup(m.awayTeam, m.awayLineup, m.awayAutomatic)}</div>` : `<div class="lineup-wait"><span>${m.homeSubmitted ? "✓" : "○"} ${esc(m.homeTeam)}</span><span>${m.awaySubmitted ? "✓" : "○"} ${esc(m.awayTeam)}</span><small>Lineups stay hidden until both teams submit.</small></div>`}${state.user.role === "MANAGER" ? `<div class="toolbar"><input class="score-home inline-input" data-id="${m.id}" type="number" min="0" value="${m.homeScore ?? ""}" placeholder="Home"><input class="score-away inline-input" data-id="${m.id}" type="number" min="0" value="${m.awayScore ?? ""}" placeholder="Away"><button class="primary save-result" data-id="${m.id}">Save Result</button>${m.played ? `<button class="secondary clear-result" data-id="${m.id}">Clear</button>` : ""}</div>` : ""}</div>`).join("")}</div>`,
        )
        .join("")}`;

    const shown = data.flatMap((m) =>
      m.revealed ? [...(m.homeLineup || []), ...(m.awayLineup || [])] : [],
    );
    bindDisplayCards($("#page-schedule"), shown);

    $$(".save-result").forEach(
      (b) =>
        (b.onclick = async () => {
          const id = b.dataset.id;
          const h = document.querySelector(
            `.score-home[data-id="${id}"]`,
          ).value;
          const a = document.querySelector(
            `.score-away[data-id="${id}"]`,
          ).value;
          if (h === "" || a === "") return toast("Enter both scores", true);
          try {
            await api(`/api/manager/results/${id}`, {
              method: "POST",
              body: { homeScore: Number(h), awayScore: Number(a) },
            });
            toast("Result saved");
            await renderSchedule();
          } catch (e) {
            toast(e.message, true);
          }
        }),
    );

    $$(".clear-result").forEach(
      (b) =>
        (b.onclick = async () => {
          try {
            await api(`/api/manager/results/${b.dataset.id}`, {
              method: "DELETE",
            });
            toast("Result cleared");
            await renderSchedule();
          } catch (e) {
            toast(e.message, true);
          }
        }),
    );
  };
})();
