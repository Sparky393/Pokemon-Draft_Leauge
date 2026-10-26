/* League-flow UI fixes loaded after all other UI overrides. */
function simplePokemonCard(p, selected = false) {
  const id = p.pokemonId ?? p.id;
  return `<button type="button" class="pokemon-card league-select-card ${selected ? "selected" : ""}" data-pokemon-id="${id}" data-pokemon-name="${esc(p.name)}"><div class="pokemon-art"><div class="poke-loader">•••</div><img alt="${esc(p.name)}" loading="lazy"></div><div class="pokemon-card-body"><strong>${esc(p.name)}</strong><div class="poke-meta muted"></div></div></button>`;
}

renderLineup = async function () {
  if (state.user.role === "MANAGER") {
    $("#page-lineup").innerHTML =
      '<h1>Weekly Lineup</h1><div class="card"><p>Coach lineups appear on Schedule as soon as both teams submit.</p></div>';
    return;
  }
  const d = await api("/api/flow/lineup");
  if (!d.lineupRequired) {
    $("#page-lineup").innerHTML =
      `<h1>Weekly Lineup</h1><div class="card"><h2>Week ${d.week}: ${esc(d.weekLabel)}</h2><p class="muted">No match or lineup submission is scheduled this week. Submissions will reopen in the next playing week.</p></div>`;
    return;
  }
  const selected = new Set(d.selectedPokemonIds || []);
  $("#page-lineup").innerHTML =
    `<div class="section-title"><div><h1>Weekly Lineup</h1><p class="muted">Week ${d.week} · choose exactly 6 of your 10 Pokémon.</p></div><span id="lineup-count" class="pool-count">${selected.size}/6 selected</span></div><div class="card"><p class="muted">${d.locked ? "Both teams submitted. Your lineup is locked." : `Deadline: ${d.deadline ? new Date(d.deadline).toLocaleString() : "No deadline set"}`}</p><div id="lineup-grid" class="pokemon-card-grid lineup-draft-grid">${d.roster.map((p) => simplePokemonCard(p, selected.has(p.pokemonId))).join("")}</div><div class="selection-footer"><span id="lineup-message">${d.locked ? "Lineup locked and revealed on Schedule." : "Select six Pokémon."}</span><button id="submit-lineup" class="primary" ${d.locked ? "disabled" : ""}>Submit Team of 6</button></div></div>`;
  const grid = $("#lineup-grid");
  hydratePokemonCards(grid);
  grid.querySelectorAll(".league-select-card").forEach(
    (c) =>
      (c.onclick = () => {
        if (d.locked) {
          const p = d.roster.find((x) => x.pokemonId === Number(c.dataset.pokemonId),);
          if (p) showPokemonDetails(p);
          return;
        }
        const id = Number(c.dataset.pokemonId);
        if (selected.has(id)) {
          selected.delete(id);
          c.classList.remove("selected");
        } else {
          if (selected.size >= 6) return toast("You can only select 6 Pokémon", true);
          selected.add(id);
          c.classList.add("selected");
        }
        $("#lineup-count").textContent = `${selected.size}/6 selected`;
      }),
  );
  grid.querySelectorAll(".league-select-card").forEach(
    (c) =>
      (c.ondblclick = () => {
        const p = d.roster.find((x) => x.pokemonId === Number(c.dataset.pokemonId),);
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

renderSchedule = async function () {
  const data = await api("/api/flow/schedule");
  const weeks = [...new Set(data.map((m) => Number(m.week)))].sort((a, b) => a - b,),
    teamNames = [...new Set(data.flatMap((m) => [m.homeTeam, m.awayTeam])),].sort((a, b) =>
      a.localeCompare(b)
    );
  const lineupCards = (list) =>
    `<div class="schedule-six-grid">${(list || []).map((p) => simplePokemonCard(p)).join("")}</div>`;
  $("#page-schedule").innerHTML =
    `<div class="section-title"><div><h1>Schedule / Results</h1><p class="muted">Lineups stay hidden until both coaches submit. Filter the schedule by week, team, or both.</p></div></div><div class="card"><div class="toolbar schedule-filters"><label>Week <select id="schedule-week"><option value="all">All Weeks</option>${weeks.map((w) => `<option value="${w}">Week ${w}</option>`).join("")}</select></label><label>Team <select id="schedule-team"><option value="all">All Teams</option>${teamNames.map((t) => `<option value="${esc(t)}">${esc(t)}</option>`).join("")}</select></label><button type="button" id="schedule-clear" class="secondary">Clear Filters</button></div></div><div id="schedule-results"></div>`;
  const draw = () => {
    const week = $("#schedule-week").value,
      team = $("#schedule-team").value;
    const filtered = data.filter(
        (m) =>
          (week === "all" || String(m.week) === week) &&
          (team === "all" || m.homeTeam === team || m.awayTeam === team),
      ),
      by = {};
    filtered.forEach((m) => (by[m.week] ??= []).push(m));
    const target = $("#schedule-results");
    target.innerHTML =
      Object.keys(by)
        .sort((a, b) => Number(a) - Number(b))
        .map(
          (w) =>
            `<div class="card week"><h2>Week ${w}</h2>${by[w].map((m) => `<div class="matchup matchup-with-lineups"><div class="matchup-score"><strong>${esc(m.homeTeam)}</strong><span>${m.played ? m.homeScore : "—"}</span><strong>${esc(m.awayTeam)}</strong><span>${m.played ? m.awayScore : "—"}</span></div>${m.revealed ? `<div class="lineup-reveal"><div>${lineupCards(m.homeLineup)}</div><div>${lineupCards(m.awayLineup)}</div></div>` : `<p class="muted lineup-wait">${m.homeSubmitted ? "✓" : "○"} ${esc(m.homeTeam)} submitted · ${m.awaySubmitted ? "✓" : "○"} ${esc(m.awayTeam)} submitted — lineups hidden</p>`}${state.user.role === "MANAGER" ? `<div class="toolbar"><input class="score-home inline-input" data-id="${m.id}" type="number" min="0" value="${m.homeScore ?? ""}" placeholder="Home"><input class="score-away inline-input" data-id="${m.id}" type="number" min="0" value="${m.awayScore ?? ""}" placeholder="Away"><button class="primary save-result" data-id="${m.id}">Save Result</button>${m.played ? `<button class="secondary clear-result" data-id="${m.id}">Clear</button>` : ""}</div>` : ""}</div>`).join("")}</div>`,
        )
        .join("") ||
      '<div class="card"><p class="muted">No matchups match those filters.</p></div>';
    hydratePokemonCards(target);
    target.querySelectorAll(".league-select-card").forEach(
      (c) =>
        (c.onclick = () => {
          const id = Number(c.dataset.pokemonId),
            p = data
              .flatMap((m) => [...(m.homeLineup || []), ...(m.awayLineup || []),])
              .find((x) => Number(x.pokemonId ?? x.id) === id);
          if (p) showPokemonDetails(p);
        }),
    );
    target.querySelectorAll(".save-result").forEach(
      (b) =>
        (b.onclick = async () => {
          const id = b.dataset.id,
            h = target.querySelector(`.score-home[data-id="${id}"]`).value,
            a = target.querySelector(`.score-away[data-id="${id}"]`).value;
          if (h === "" || a === "")
            return toast("Enter both scores before saving the result.", true);
          if (Number(h) < 0 || Number(a) < 0) return toast("Scores cannot be negative.", true);
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
    target.querySelectorAll(".clear-result").forEach(
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
  $("#schedule-week").onchange = draw;
  $("#schedule-team").onchange = draw;
  $("#schedule-clear").onclick = () => {
    $("#schedule-week").value = "all";
    $("#schedule-team").value = "all";
    draw();
  };
  draw();
};

renderBids = async function () {
  const list = await api("/api/free-agency/bids"),
    free = await api("/api/pokemon/free-agents");
  if (state.user.role === "MANAGER") {
    $("#fa-content").innerHTML =
      `<div class="card"><div class="section-title"><div><h2>Week 4 Waiver Processing</h2><p class="muted">Two periods (Nov 25–27 and Nov 28–30). Each coach has $3 FAAB total across both periods. Processing is commissioner-controlled.</p><p class="muted">Completed: ${state.settings.waiverPeriodsProcessed ?? 0} / 2 periods.</p></div><button id="process-bids" class="primary" ${state.settings.waiverWindowOpen ? "" : "disabled"}>Process Period ${Math.min((state.settings.waiverPeriodsProcessed ?? 0) + 1, 2)}</button></div></div><div class="card"><h2>All Bids</h2>${bidTable(list)}</div>`;
    $("#process-bids").onclick = async () => {
      try {
        const r = await api("/api/manager/free-agency/process", {
          method: "POST",
        });
        toast(`Waiver period ${r.period} processed: ${r.winners} winners`);
        await refreshBase();
        await renderBids();
      } catch (e) {
        toast(e.message, true);
      }
    };
    return;
  }
  const roster = await api(`/api/rosters/${state.user.teamId}`),
    me = state.teams.find((t) => t.id === state.user.teamId),
    currentValue = roster.reduce((s, p) => s + Number(p.price || 0), 0);
  let wanted = null;
  $("#fa-content").innerHTML =
    `<div class="card"><div class="section-title"><div><h2>Choose a Free Agent</h2><p class="muted">Click a card just like the draft.</p></div><span class="pool-count">Roster $${currentValue}/$100</span></div>${finalFilterBar("fa", true)}<div id="fa-grid" class="pokemon-card-grid force-fa-grid"></div><div class="selection-footer faab-footer"><div><strong id="fa-choice">No free agent selected</strong><div id="fa-value-preview" class="muted">Choose who you would drop if you win.</div></div><div class="toolbar"><select id="bid-drop"><option value="">${roster.length >= 10 ? "Choose Pokémon to drop" : "No drop / open slot"}</option>${roster.map((p) => `<option value="${p.pokemonId}">${esc(p.name)} · $${p.price}</option>`).join("")}</select><input id="bid-amount" type="number" min="1" max="${me?.faBudget ?? 0}" placeholder="FAAB $"><button id="submit-bid" class="primary" disabled>Submit Blind Bid</button></div></div></div><div class="card"><h2>My Bids</h2>${bidTable(list)}</div>`;
  const preview = () => {
    const w = free.find((p) => p.id === wanted),
      drop = roster.find((p) => p.pokemonId === Number($("#bid-drop").value));
    if (!w) {
      $("#submit-bid").disabled = true;
      return;
    }
    const value = currentValue + (w.price || 0) - (drop?.price || 0);
    $("#fa-value-preview").innerHTML =
      `If won: roster value <strong>$${value}/$100</strong>${value > 100 ? " · OVER BUDGET" : ""}`;
    $("#fa-value-preview").classList.toggle("budget-error", value > 100);
    $("#submit-bid").disabled = value > 100 || (roster.length >= 10 && !drop);
  };
  const draw = (rows) => {
    const grid = $("#fa-grid");
    grid.innerHTML = rows.map((p) => pokemonCard(p, wanted === p.id ? "selected" : "")).join("");
    grid.querySelectorAll(".pokemon-card").forEach(
      (c) =>
        (c.onclick = () => {
          wanted = Number(c.dataset.pokemonId);
          const p = free.find((x) => x.id === wanted);
          grid
            .querySelectorAll(".pokemon-card")
            .forEach((x) => x.classList.toggle("selected", x === c));
          $("#fa-choice").textContent = `Wanted: ${p.name} · $${p.price}`;
          preview();
        }),
    );
    hydratePokemonCards(grid);
  };
  draw(free);
  wireFinalFilters("fa", free, draw);
  $("#bid-drop").onchange = preview;
  $("#submit-bid").onclick = async () => {
    const drop = $("#bid-drop").value ? Number($("#bid-drop").value) : null,
      amount = Number($("#bid-amount").value);
    if (!wanted) return toast("Select a free agent", true);
    if (roster.length >= 10 && !drop) return toast("Choose the Pokémon to drop if you win", true);
    if (!amount || amount < 1) return toast("Bid at least $1 FAAB", true);
    const w = free.find((p) => p.id === wanted),
      dp = roster.find((p) => p.pokemonId === drop),
      value = currentValue + (w?.price || 0) - (dp?.price || 0);
    if (value > 100) return toast(`That move would make your roster $${value}/$100`, true);
    try {
      await api("/api/free-agency/bids", {
        method: "POST",
        body: { wantedPokemonId: wanted, dropPokemonId: drop, amount },
      });
      toast("Blind bid submitted");
      await renderBids();
    } catch (e) {
      toast(e.message, true);
    }
  };
};

renderTrades = async function () {
  const list = await api("/api/trades");
  if (state.user.role === "MANAGER") {
    $("#fa-content").innerHTML = `<div class="card"><h2>All Trades</h2>${tradeTable(list)}</div>`;
    bindTradeButtons();
    return;
  }
  const mine = await api(`/api/rosters/${state.user.teamId}`),
    all = await api("/api/rosters");
  let recipient = null,
    offered = null,
    requested = null;
  $("#fa-content").innerHTML =
    `<div class="card"><div class="section-title"><div><h2>Propose Trade</h2><p class="muted">Choose a team, then click one Pokémon card from each side.</p></div></div><label class="trade-team-picker">Trade with <select id="trade-recipient"><option value="">Choose team</option>${state.teams
      .filter((t) => t.id !== state.user.teamId)
      .map((t) => `<option value="${t.id}">${esc(t.name)}</option>`)
      .join(
        "",
      )}</select></label><div class="trade-card-columns"><div><h3>Your Pokémon</h3><div id="trade-mine" class="pokemon-card-grid trade-pokemon-grid">${mine.map((p) => simplePokemonCard(p)).join("")}</div></div><div><h3 id="trade-their-title">Their Pokémon</h3><div id="trade-theirs" class="pokemon-card-grid trade-pokemon-grid"><p class="muted">Choose a team first.</p></div></div></div><div class="selection-footer"><span id="trade-summary" class="muted">Select one Pokémon from each team.</span><button id="offer-trade" class="primary" disabled>Send Trade Offer</button></div></div><div class="card"><h2>My Trades</h2>${tradeTable(list)}</div>`;
  const enable = () => {
    $("#offer-trade").disabled = !(recipient && offered && requested);
    if (recipient && offered && requested) {
      const a = mine.find((p) => p.pokemonId === offered),
        r = all.find((x) => x.team.id === recipient),
        b = r?.pokemon.find((p) => p.pokemonId === requested);
      $("#trade-summary").innerHTML =
        `Offer <strong>${esc(a?.name)}</strong> for <strong>${esc(b?.name)}</strong>`;
    }
  };
  const bindMine = () => {
    $$("#trade-mine .league-select-card").forEach(
      (c) =>
        (c.onclick = () => {
          offered = Number(c.dataset.pokemonId);
          $$("#trade-mine .league-select-card").forEach((x) =>
            x.classList.toggle("selected", x === c),
          );
          enable();
        }),
    );
  };
  bindMine();
  hydratePokemonCards($("#trade-mine"));
  $("#trade-recipient").onchange = () => {
    recipient = Number($("#trade-recipient").value) || null;
    requested = null;
    const r = all.find((x) => x.team.id === recipient);
    $("#trade-their-title").textContent = r ? r.team.name : "Their Pokémon";
    $("#trade-theirs").innerHTML = r
      ? r.pokemon.map((p) => simplePokemonCard(p)).join("")
      : '<p class="muted">Choose a team first.</p>';
    $$("#trade-theirs .league-select-card").forEach(
      (c) =>
        (c.onclick = () => {
          requested = Number(c.dataset.pokemonId);
          $$("#trade-theirs .league-select-card").forEach((x) =>
            x.classList.toggle("selected", x === c),
          );
          enable();
        }),
    );
    hydratePokemonCards($("#trade-theirs"));
    enable();
  };
  $("#offer-trade").onclick = async () => {
    try {
      await api("/api/flow/trades", {
        method: "POST",
        body: {
          recipientTeamId: recipient,
          offeredPokemonId: offered,
          requestedPokemonId: requested,
          proposerFaab: 0,
          recipientFaab: 0,
        },
      });
      toast("Trade offered");
      await renderTrades();
    } catch (e) {
      toast(e.message, true);
    }
  };
  bindTradeButtons();
};
