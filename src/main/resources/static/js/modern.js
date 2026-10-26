/* Modern view overrides. Loaded after app.js so the existing API/auth logic stays intact. */
const teamOptions = (all = true) =>
  `${all ? '<option value="all">All Teams</option>' : ""}${state.teams.map((t) => `<option value="${t.id}">${esc(t.name)}</option>`).join("")}`;
const monCard = (p, extra = "") =>
  `<div class="mon-card ${extra}" data-mon-id="${p.id ?? p.pokemonId}"><span class="mon-price">$${p.price ?? 0}</span><div class="mon-name">${esc(p.name)}</div><div class="mon-meta">${p.drafted ? "Drafted" : "Pokemon"}${p.price != null ? ` · Value ${p.price}` : ""}</div></div>`;
const hero = (label, title, copy, actions = "") =>
  `<div class="page-hero"><div><div class="eyebrow">${label}</div><h1>${title}</h1><p class="muted">${copy}</p></div>${actions}</div>`;

renderHome = async function (initialDashboard) {
  const d = initialDashboard || (await api("/api/dashboard/view"));
  state.teams = d.teams;
  state.settings = d.settings;
  const my = state.user.teamId ? state.teams.find((t) => t.id === state.user.teamId) : null;
  $("#page-home").innerHTML =
    `${hero("League Portal", esc(d.settings.leagueName), `Week ${d.settings.currentWeek} of ${d.settings.regularSeasonWeeks}`)}<div class="grid"><div class="card"><div class="eyebrow">League</div><div class="stat">16</div><p class="muted">Coaches across two divisions</p></div><div class="card"><div class="eyebrow">Draft</div><div class="stat">${d.settings.draftOpen ? "OPEN" : "CLOSED"}</div><p class="muted">${d.currentDraftTeam ? `${esc(d.currentDraftTeam.name)} is on the clock` : "Draft complete"}</p></div><div class="card"><div class="eyebrow">Playoffs</div><div class="stat">${d.settings.playoffSeedModeEnabled ? "ON" : "OFF"}</div><p class="muted">Scenario mode ${d.settings.playoffSeedModeEnabled ? "available" : "locked until final two weeks"}</p></div>${my ? `<div class="card"><div class="eyebrow">My Team</div><h2 class="my-team-home">${teamBrand(my, "md")}</h2><div class="stat">${my.wins}-${my.losses}</div><p class="muted">${my.differential >= 0 ? "+" : ""}${my.differential} differential · $${my.faBudget} FAAB</p></div>` : ""}</div>`;
};

renderDraft = async function () {
  const d = await api("/api/draft");
  let selected = null;
  const canPick =
    state.user.role === "MANAGER" || (d.currentTeam && d.currentTeam.id === state.user.teamId);
  $("#page-draft").innerHTML =
    `${hero("Live Draft", "Draft Room", `10 rounds · ${d.settings.snakeDraft ? "Snake" : "Linear"} format`, state.user.role === "MANAGER" ? '<button id="undo-pick" class="danger">Undo Last Pick</button>' : "")}<div class="card"><div class="eyebrow">On The Clock</div><h2>${d.currentTeam ? esc(d.currentTeam.name) : "Draft Complete"}</h2>${d.currentTeam ? `<p class="muted">Draft position ${d.currentTeam.draftPosition}</p>` : ""}</div><div class="filter-bar"><input id="draft-search" placeholder="Search available Pokemon..."><select id="draft-price"><option value="all">All Values</option><option value="high">$15–$20</option><option value="mid">$8–$14</option><option value="low">$0–$7</option></select></div><div id="draft-grid" class="pokemon-card-grid"></div><div class="selection-footer"><div id="draft-selection" class="muted">Select a Pokemon from the grid</div><button id="make-pick" class="primary" disabled>Draft Pokemon</button></div><div class="card" style="margin-top:24px"><h2>Draft Board</h2><div class="table-wrap"><table><thead><tr><th>Pick</th><th>Round</th><th>Team</th><th>Pokemon</th></tr></thead><tbody>${d.picks.map((p) => `<tr><td>${p.overallPick}</td><td>${p.round}</td><td>${esc(p.teamName)}</td><td>${esc(p.pokemonName)}</td></tr>`).join("") || '<tr><td colspan="4">No picks yet</td></tr>'}</tbody></table></div></div>`;
  const draw = () => {
    const q = $("#draft-search").value.toLowerCase(),
      price = $("#draft-price").value;
    const list = d.available.filter(
      (p) =>
        p.name.toLowerCase().includes(q) &&
        (price === "all" ||
          (price === "high" && p.price >= 15) ||
          (price === "mid" && p.price >= 8 && p.price <= 14) ||
          (price === "low" && p.price <= 7)),
    );
    $("#draft-grid").innerHTML =
      list.map((p) => monCard(p, `clickable ${selected === p.id ? "selected" : ""}`),).join("") ||
      '<div class="empty-state">No available Pokemon match your search.</div>';
    $$("#draft-grid .mon-card").forEach(
      (c) =>
        (c.onclick = () => {
          selected = Number(c.dataset.monId);
          const p = d.available.find((x) => x.id === selected);
          $("#draft-selection").innerHTML =
            `Selected <strong>${esc(p.name)}</strong> · $${p.price}`;
          $("#make-pick").disabled = !canPick;
          draw();
        }),
    );
  };
  $("#draft-search").oninput = draw;
  $("#draft-price").onchange = draw;
  draw();
  $("#make-pick").onclick = async () => {
    if (!selected) return;
    try {
      await api("/api/draft/pick", {
        method: "POST",
        body: { pokemonId: selected },
      });
      toast("Pick recorded");
      await renderDraft();
    } catch (e) {
      toast(e.message, true);
    }
  };
  if ($("#undo-pick"))
    $("#undo-pick").onclick = async () => {
      try {
        await api("/api/manager/draft/undo", { method: "POST" });
        toast("Last pick undone");
        await renderDraft();
      } catch (e) {
        toast(e.message, true);
      }
    };
};

renderRosters = async function () {
  const data = await api("/api/rosters");
  $("#page-rosters").innerHTML =
    `${hero("League Teams", "Rosters", "Filter by team or browse every roster.")}<div class="filter-bar"><select id="roster-team">${teamOptions(true)}</select><input id="roster-search" placeholder="Search Pokemon or team..."></div><div id="roster-results"></div>`;
  const draw = () => {
    const team = $("#roster-team").value,
      q = $("#roster-search").value.toLowerCase();
    const rows = data.filter(
      (r) =>
        (team === "all" || String(r.team.id) === team) &&
        (r.team.name.toLowerCase().includes(q) ||
          r.team.coachName.toLowerCase().includes(q) ||
          r.pokemon.some((p) => p.name.toLowerCase().includes(q))),
    );
    $("#roster-results").innerHTML =
      `<div class="grid">${rows.map((r) => `<div class="team-view glass-panel"><div class="team-view-header"><div><div class="eyebrow">Division ${r.team.division}</div><h2>${esc(r.team.name)}</h2><p class="muted">${esc(r.team.coachName)}</p></div><span class="pill">${r.pokemon.length}/10</span></div><div class="roster-grid">${r.pokemon.map((p) => monCard(p)).join("") || '<div class="empty-state">No Pokemon drafted yet.</div>'}</div></div>`).join("")}</div>`;
  };
  $("#roster-team").onchange = draw;
  $("#roster-search").oninput = draw;
  draw();
};

renderStandings = async function () {
  const s = await api("/api/standings");
  $("#page-standings").innerHTML =
    `${hero("Regular Season", "Standings", "Wins → Differential → Head-to-Head → Team")}<div class="filter-bar"><select id="standing-team">${teamOptions(true)}</select></div><div id="standing-results"></div>`;
  const draw = () => {
    const id = $("#standing-team").value;
    const panel = (division, rows) => {
      const filtered = id === "all" ? rows : rows.filter((t) => String(t.id) === id);
      return `<div class="glass-panel team-view"><div class="eyebrow">Division ${division}</div><h2>Division ${division}</h2><div class="standing-row standing-labels"><span>#</span><span>Team</span><span>W</span><span>L</span><span>Diff</span></div>${filtered.map((t, i) => `<div class="standing-row"><span class="standing-rank">${rows.indexOf(t) + 1}</span><span class="standing-team">${esc(t.name)}</span><span>${t.wins}</span><span>${t.losses}</span><span>${t.differential >= 0 ? "+" : ""}${t.differential}</span></div>`).join("") || '<div class="empty-state">Team is in the other division.</div>'}</div>`;
    };
    $("#standing-results").innerHTML =
      `<div class="standings-grid">${panel("A", s.A)}${panel("B", s.B)}</div>`;
  };
  $("#standing-team").onchange = draw;
  draw();
};

renderSchedule = async function () {
  const data = await api("/api/schedule");
  const weeks = [...new Set(data.map((m) => m.week))].sort((a, b) => a - b);
  $("#page-schedule").innerHTML =
    `${hero("Season", "Schedule / Results", "Filter the full schedule by week and team.")}<div class="filter-bar"><select id="schedule-week"><option value="all">All Weeks</option>${weeks.map((w) => `<option value="${w}">Week ${w}</option>`).join("")}</select><select id="schedule-team">${teamOptions(true)}</select></div><div id="schedule-grid" class="match-grid"></div>`;
  const draw = () => {
    const w = $("#schedule-week").value,
      t = $("#schedule-team").value;
    const team = t === "all" ? null : state.teams.find((x) => String(x.id) === t);
    const list = data.filter(
      (m) =>
        (w === "all" || String(m.week) === w) &&
        (!team || m.homeTeam === team.name || m.awayTeam === team.name),
    );
    $("#schedule-grid").innerHTML =
      list
        .map(
          (m) =>
            `<div class="match-card"><div class="match-week">Week ${m.week}</div><div class="match-row"><span>${esc(m.homeTeam)}</span><span class="score">${m.played ? m.homeScore : "—"}</span></div><div class="match-row"><span>${esc(m.awayTeam)}</span><span class="score">${m.played ? m.awayScore : "—"}</span></div>${state.user.role === "MANAGER" ? `<div class="match-actions"><input class="score-home inline-input glass-input" data-id="${m.id}" type="number" min="0" value="${m.homeScore ?? ""}" placeholder="Home"><input class="score-away inline-input glass-input" data-id="${m.id}" type="number" min="0" value="${m.awayScore ?? ""}" placeholder="Away"><button class="primary save-result" data-id="${m.id}">Save</button>${m.played ? `<button class="secondary clear-result" data-id="${m.id}">Clear</button>` : ""}</div>` : ""}</div>`,
        )
        .join("") || '<div class="empty-state">No matchups match these filters.</div>';
    $$(".save-result").forEach(
      (b) =>
        (b.onclick = async () => {
          const id = b.dataset.id,
            h = document.querySelector(`.score-home[data-id="${id}"]`).value,
            a = document.querySelector(`.score-away[data-id="${id}"]`).value;
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
  $("#schedule-week").onchange = draw;
  $("#schedule-team").onchange = draw;
  draw();
};

renderLineup = async function () {
  if (state.user.role === "MANAGER") {
    $("#page-lineup").innerHTML =
      `${hero("Weekly Team", "Weekly Lineup", "Coach submissions stay private until the deadline.")}<div class="card"><p>Commissioner accounts do not submit a weekly team.</p></div>`;
    return;
  }
  const d = await api("/api/lineup");
  let selected = new Set(
    (d.submission?.pokemonIdsCsv || "").split(",").filter(Boolean).map(Number),
  );
  $("#page-lineup").innerHTML =
    `${hero("Week " + d.week, "Weekly Lineup", "Choose exactly six Pokemon from your roster.")}<div class="card"><p class="muted">Deadline: ${d.deadline ? new Date(d.deadline).toLocaleString() : "No deadline set yet"}</p><div id="lineup-grid" class="pokemon-card-grid"></div></div><div class="selection-footer"><strong id="lineup-count"></strong><button id="submit-lineup" class="primary">Submit Team of 6</button></div>`;
  const draw = () => {
    $("#lineup-grid").innerHTML =
      d.roster
        .map(
          (p) =>
            `<div class="mon-card clickable ${selected.has(p.pokemonId) ? "selected" : ""}" data-mon-id="${p.pokemonId}"><div class="mon-name">${esc(p.name)}</div><div class="mon-meta">${selected.has(p.pokemonId) ? "Selected" : "Click to select"}</div></div>`,
        )
        .join("") || '<div class="empty-state">No roster yet.</div>';
    $("#lineup-count").textContent = `${selected.size} / 6 selected`;
    $$("#lineup-grid .mon-card").forEach(
      (c) =>
        (c.onclick = () => {
          const id = Number(c.dataset.monId);
          if (selected.has(id)) selected.delete(id);
          else if (selected.size < 6) selected.add(id);
          else return toast("You can only select 6 Pokemon", true);
          draw();
        }),
    );
  };
  draw();
  $("#submit-lineup").onclick = async () => {
    if (selected.size !== 6) return toast("Select exactly 6 Pokemon", true);
    try {
      await api("/api/lineup", {
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

renderBids = async function () {
  const list = await api("/api/free-agency/bids");
  const free = await api("/api/pokemon/free-agents");
  if (state.user.role === "MANAGER") {
    $("#fa-content").innerHTML =
      `<div class="card"><div class="section-title"><div><div class="eyebrow">Commissioner</div><h2>Blind FAAB Processing</h2><p class="muted">All undrafted Pokemon remain in the free-agent pool.</p></div><button id="process-bids" class="primary">Process Pending Bids</button></div></div><div class="filter-bar"><input id="fa-search" placeholder="Search all free agents..."></div><div id="fa-grid" class="pokemon-card-grid"></div><div style="margin-top:22px">${bidTable(list)}</div>`;
    const draw = () => {
      $("#fa-grid").innerHTML =
        free
          .filter((p) => p.name.toLowerCase().includes($("#fa-search").value.toLowerCase()),)
          .map((p) => monCard(p))
          .join("") || '<div class="empty-state">No free agents match.</div>';
    };
    $("#fa-search").oninput = draw;
    draw();
    $("#process-bids").onclick = async () => {
      try {
        const r = await api("/api/manager/free-agency/process", {
          method: "POST",
        });
        toast(`Processed bids: ${r.winners} winners`);
        await renderBids();
      } catch (e) {
        toast(e.message, true);
      }
    };
    return;
  }
  const roster = await api(`/api/rosters/${state.user.teamId}`),
    me = state.teams.find((t) => t.id === state.user.teamId);
  let wanted = null;
  $("#fa-content").innerHTML =
    `<div class="card"><div class="eyebrow">Available Pool</div><h2>Free Agents</h2><p class="muted">Every Pokemon not currently drafted is shown here.</p><div class="filter-bar"><input id="fa-search" placeholder="Search free agents..."></div><div id="fa-grid" class="pokemon-card-grid"></div><div class="selection-footer"><span id="fa-selected" class="muted">Select a Pokemon</span><div class="toolbar" style="margin:0"><select id="bid-drop"><option value="">No drop / open slot</option>${roster.map((p) => `<option value="${p.pokemonId}">${esc(p.name)}</option>`).join("")}</select><input id="bid-amount" class="glass-input" type="number" min="0" max="${me?.faBudget ?? 0}" placeholder="FAAB $"><button id="submit-bid" class="primary" disabled>Submit Bid</button></div></div></div><div class="card"><h2>My Bids</h2>${bidTable(list)}</div>`;
  const draw = () => {
    $("#fa-grid").innerHTML =
      free
        .filter((p) => p.name.toLowerCase().includes($("#fa-search").value.toLowerCase()),)
        .map((p) => monCard(p, `clickable ${wanted === p.id ? "selected" : ""}`),)
        .join("") || '<div class="empty-state">No free agents match.</div>';
    $$("#fa-grid .mon-card").forEach(
      (c) =>
        (c.onclick = () => {
          wanted = Number(c.dataset.monId);
          const p = free.find((x) => x.id === wanted);
          $("#fa-selected").innerHTML =
            `Selected <strong>${esc(p.name)}</strong> · Budget $${me?.faBudget ?? 0}`;
          $("#submit-bid").disabled = false;
          draw();
        }),
    );
  };
  $("#fa-search").oninput = draw;
  draw();
  $("#submit-bid").onclick = async () => {
    const amount = Number($("#bid-amount").value),
      drop = $("#bid-drop").value;
    if (wanted == null || Number.isNaN(amount))
      return toast("Select a Pokemon and enter a bid", true);
    try {
      await api("/api/free-agency/bids", {
        method: "POST",
        body: {
          pokemonWantedId: wanted,
          pokemonDropId: drop ? Number(drop) : null,
          amount,
        },
      });
      toast("Blind bid submitted");
      await renderBids();
    } catch (e) {
      toast(e.message, true);
    }
  };
};
