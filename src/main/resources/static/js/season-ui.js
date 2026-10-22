/* Published Season 2 schedule and playoff presentation. Loaded after older UI layers. */
let seasonSelectedWeek = "all";
let seasonSelectedTeam = "all";

function seasonMatchCard(m, locked = false) {
  const maxScore = m.week >= 14 ? 2 : 3;
  const score = m.played ? m.homeScore + " – " + m.awayScore : "vs";
  const status = m.revealed
    ? '<div class="lineup-reveal"><div><h4>' +
      teamBrand(m.homeTeamId) +
      '</h4><div class="schedule-six-grid">' +
      (m.homeLineup || []).map((p) => simplePokemonCard(p)).join("") +
      "</div></div><div><h4>" +
      teamBrand(m.awayTeamId) +
      '</h4><div class="schedule-six-grid">' +
      (m.awayLineup || []).map((p) => simplePokemonCard(p)).join("") +
      "</div></div></div>"
    : '<p class="muted season-lineup-status">' +
      (m.homeSubmitted ? "✓" : "○") +
      " " +
      teamBrand(m.homeTeamId) +
      " · " +
      (m.awaySubmitted ? "✓" : "○") +
      " " +
      teamBrand(m.awayTeamId) +
      " — lineups hidden until both submit</p>";
  const tools = locked
    ? '<p class="season-locked-note">🔒 Final results — locked by commissioner</p>'
    : state.user.role === "MANAGER"
      ? `<div class="toolbar season-score-controls">
         <input class="score-home inline-input" data-id="${m.id}" type="number" min="0" max="${maxScore}" step="1" value="${m.homeScore ?? ""}" placeholder="Home score">
         <input class="score-away inline-input" data-id="${m.id}" type="number" min="0" max="${maxScore}" step="1" value="${m.awayScore ?? ""}" placeholder="Away score">
         <button class="primary save-result" data-id="${m.id}" data-week="${m.week}">Save Result</button>
         ${m.played ? `<button class="secondary clear-result" data-id="${m.id}">Clear</button>` : ""}
       </div>`
      : "";
  return `<div class="season-match ${m.revealed ? "season-match--revealed" : ""}">
    <div class="season-versus"><strong>${teamBrand(m.homeTeamId)}</strong><span>${score}</span><strong>${teamBrand(m.awayTeamId)}</strong></div>
    ${status}${tools}</div>`;
}

function wireSeasonScores(scope, refresh, playoff = false) {
  scope.querySelectorAll(".save-result").forEach((button) => {
    button.onclick = async () => {
      const id = button.dataset.id;
      const home = scope.querySelector(`.score-home[data-id="${id}"]`)?.value;
      const away = scope.querySelector(`.score-away[data-id="${id}"]`)?.value;
      if (
        home === "" ||
        away === "" ||
        !Number.isInteger(Number(home)) ||
        !Number.isInteger(Number(away)) ||
        Number(home) < 0 ||
        Number(away) < 0
      )
        return toast("Enter two nonnegative whole-number scores.", true);
      const isPlayoff = playoff || Number(button.dataset.week) >= 14;
      const homeWins = Number(home),
        awayWins = Number(away);
      const valid = isPlayoff
        ? (homeWins === 2 && [0, 1].includes(awayWins)) ||
          (awayWins === 2 && [0, 1].includes(homeWins))
        : homeWins + awayWins === 3 && homeWins <= 3 && awayWins <= 3;
      if (!valid)
        return toast(
          isPlayoff
            ? "Playoff scores must be 2–0, 2–1, 0–2 or 1–2."
            : "Regular-season scores must be 3–0, 2–1, 1–2 or 0–3.",
          true,
        );
      try {
        await api(`/api/manager/results/${id}`, {
          method: "POST",
          body: { homeScore: Number(home), awayScore: Number(away) },
        });
        toast("Result saved");
        await refresh();
      } catch (error) {
        toast(error.message, true);
      }
    };
  });
  scope.querySelectorAll(".clear-result").forEach((button) => {
    button.onclick = async () => {
      if (
        playoff &&
        !confirm(
          "Clear this playoff result? Later-round fixtures may be reset.",
        )
      )
        return;
      try {
        await api(`/api/manager/results/${button.dataset.id}`, {
          method: "DELETE",
        });
        toast("Result cleared");
        await refresh();
      } catch (error) {
        toast(error.message, true);
      }
    };
  });
}

renderSchedule = async function () {
  const root = $("#page-schedule");
  const renderId = String(Number(root.dataset.scheduleGeneration || 0) + 1);
  root.dataset.scheduleGeneration = renderId;
  const scheduleQuery = () => {
    const params = new URLSearchParams();
    if (seasonSelectedWeek !== "all") params.set("week", seasonSelectedWeek);
    if (seasonSelectedTeam !== "all") params.set("teamId", seasonSelectedTeam);
    return "/api/flow/schedule" + (params.size ? "?" + params : "");
  };
  const initialQuery = scheduleQuery();
  const [initialGames, calendar, lockedWeeks] = await Promise.all([
    api(initialQuery),
    api("/api/flow/calendar"),
    api("/api/weeks/locks"),
  ]);
  if (root.dataset.scheduleGeneration !== renderId) return;
  let games = initialGames;
  // Reuse an already loaded full schedule within this render. Scoring or page
  // navigation creates a fresh render, so this never hides new saved results.
  let allGames = initialQuery === "/api/flow/schedule" ? initialGames : null;
  let filterRequest = 0;
  const options = state.teams
    .slice()
    .sort((a, b) => a.name.localeCompare(b.name));
  root.innerHTML = `
    <div class="page-hero"><div><div class="eyebrow">Official Season 2 Fixtures</div>
    <h1>Schedule / Results</h1><p class="muted">Every regular-season matchup follows the published spreadsheet. Weeks 4, 8 and 9 have no games or lineups.</p></div></div>
    <div class="card"><div class="toolbar schedule-filters">
      <label>Week <select id="schedule-week"><option value="all">All 16 Weeks</option>${calendar.map((w) => `<option value="${w.week}">Week ${w.week} · ${esc(w.label)}</option>`).join("")}</select></label>
      <label>Team <select id="schedule-team"><option value="all">All Teams</option>${options.map((t) => `<option value="${t.id}">${esc(t.name)}</option>`).join("")}</select></label>
      <button id="schedule-clear" class="secondary" type="button">Clear Filters</button>
    </div></div>
    <div id="schedule-results"></div>`;
  root.querySelector("#schedule-week").value = seasonSelectedWeek;
  root.querySelector("#schedule-team").value = seasonSelectedTeam;
  if (!root.querySelector("#schedule-week").value) {
    seasonSelectedWeek = "all";
    root.querySelector("#schedule-week").value = "all";
  }
  const draw = () => {
    seasonSelectedWeek = root.querySelector("#schedule-week").value;
    seasonSelectedTeam = root.querySelector("#schedule-team").value;
    const selected =
      seasonSelectedTeam === "all" ? null : Number(seasonSelectedTeam);
    const target = root.querySelector("#schedule-results");
    const availableWeeks = calendar.filter(
      (w) =>
        seasonSelectedWeek === "all" || Number(seasonSelectedWeek) === w.week,
    );
    target.innerHTML = availableWeeks
      .map((w) => {
        const rows = games.filter(
          (m) =>
            m.week === w.week &&
            (!selected ||
              m.homeTeamId === selected ||
              m.awayTeamId === selected),
        );
        let body;
        if (!w.lineupRequired) {
          body = `<div class="season-off-week"><strong>No matches or lineups this week.</strong>
          <p>${w.kind === "free_agency" ? "Free agency / waivers only." : "League-wide bye week."}</p>
          ${(w.waiverNotes || []).map((note) => `<p class="muted">${esc(note)}</p>`).join("")}</div>`;
        } else if (rows.length) {
          body = rows
            .map((m) =>
              seasonMatchCard(m, lockedWeeks[String(m.week)] === true),
            )
            .join("");
        } else if (w.kind === "playoffs") {
          body =
            '<div class="season-off-week">Playoff matchups will appear as seeds and winners are determined. See the Playoffs tab.</div>';
        } else {
          body =
            '<div class="season-off-week">No games match this team filter for the selected week.</div>';
        }
        return `<section class="card season-week"><div class="season-week-head">
          <div><div class="eyebrow">${esc(w.dates)}</div><h2>Week ${w.week}: ${esc(w.label)}</h2></div>
          <div>${lockedWeeks[String(w.week)] === true ? '<span class="season-week-lock">🔒 Results locked</span>' : ""}<span class="season-kind">${w.kind === "free_agency" ? "FREE AGENCY" : w.kind === "bye" ? "BYE" : w.kind === "playoffs" ? "PLAYOFFS" : "MATCHES"}</span></div>
        </div><div class="season-matches">${body}</div></section>`;
      })
      .join("");
    hydratePokemonCards(target);
    target.querySelectorAll(".league-select-card").forEach((card) => {
      card.onclick = () => {
        const id = Number(card.dataset.pokemonId);
        const p = games
          .flatMap((m) => [...(m.homeLineup || []), ...(m.awayLineup || [])])
          .find((mon) => Number(mon.pokemonId ?? mon.id) === id);
        if (p) showPokemonDetails(p);
      };
    });
    wireSeasonScores(target, renderSchedule);
  };
  const reloadGames = async () => {
    const request = ++filterRequest;
    seasonSelectedWeek = root.querySelector("#schedule-week").value;
    seasonSelectedTeam = root.querySelector("#schedule-team").value;
    if (allGames) {
      games = allGames;
      draw();
      return;
    }
    try {
      const query = scheduleQuery();
      const next = await api(query);
      if (
        request !== filterRequest ||
        root.dataset.scheduleGeneration !== renderId
      )
        return;
      if (query === "/api/flow/schedule") allGames = next;
      games = next;
      draw();
    } catch (error) {
      toast(error.message, true);
    }
  };
  root.querySelector("#schedule-week").onchange = reloadGames;
  root.querySelector("#schedule-team").onchange = reloadGames;
  root.querySelector("#schedule-clear").onclick = () => {
    root.querySelector("#schedule-week").value = "all";
    root.querySelector("#schedule-team").value = "all";
    reloadGames();
  };
  draw();
};

renderPlayoffs = async function () {
  const [bracket, lockedWeeks] = await Promise.all([
    api("/api/playoffs"),
    api("/api/weeks/locks"),
  ]);
  const root = $("#page-playoffs");
  const stage = (week, title, dates) => {
    const games = bracket.games.filter((g) => g.week === week);
    return `<section class="card playoff-stage"><div class="eyebrow">Week ${week} · ${esc(dates)}</div>
      <h2>${esc(title)}</h2><div class="playoff-games">${games
        .map((game) => {
          const score = game.played
            ? `${game.homeScore} – ${game.awayScore}`
            : "vs";
          const inputs =
            game.ready &&
            state.user.role === "MANAGER" &&
            lockedWeeks[String(week)] !== true
              ? `<div class="toolbar season-score-controls">
               <input class="score-home inline-input" data-id="${game.id}" type="number" min="0" max="2" step="1" value="${game.homeScore ?? ""}" placeholder="Home">
               <input class="score-away inline-input" data-id="${game.id}" type="number" min="0" max="2" step="1" value="${game.awayScore ?? ""}" placeholder="Away">
               <button class="primary save-result" data-id="${game.id}">Save</button>
               ${game.played ? `<button class="secondary clear-result" data-id="${game.id}">Clear</button>` : ""}
             </div>`
              : "";
          return `<div class="playoff-game ${game.ready ? "" : "awaiting"}">
          <div class="playoff-game-label">${esc(game.division)} · ${esc(game.round)}</div>
          <div class="season-versus"><strong>${teamBrand(game.homeTeam)}</strong><span>${score}</span><strong>${teamBrand(game.awayTeam)}</strong></div>
          ${inputs}
          ${lockedWeeks[String(week)] === true ? '<p class="season-locked-note">🔒 Results locked</p>' : ""}
          ${game.ready ? "" : '<div class="muted small">Waiting for qualification or earlier round results</div>'}
        </div>`;
        })
        .join("")}</div></section>`;
  };
  root.innerHTML = `<div class="page-hero"><div><div class="eyebrow">Season 2 Postseason</div>
    <h1>Playoff Bracket</h1><p class="muted">Top four from each division qualify. Kanto: 1 vs 3 and 2 vs 4. Johto: 1 vs 4 and 2 vs 3.</p></div></div>
    <div class="card playoff-status">${
      bracket.regularSeasonComplete
        ? "<strong>Regular season complete.</strong> Quarterfinal seeds are locked in from the standings. Winners advance when results are recorded."
        : `<strong>Bracket placeholders.</strong> ${bracket.regularGamesRemaining} regular-season games remain. Qualifiers are set only when all 80 matches have results.`
    }</div>
    <div class="playoff-flow">
      ${stage(14, "Divisional Quarterfinals", "February 5–9")}
      ${stage(15, "Divisional Semifinals", "February 12–16")}
      ${stage(16, "Kanto vs Johto Championship", "February 19–23")}
    </div>`;
  wireSeasonScores(root, renderPlayoffs, true);
};

// Use division names from the official fixture sheet.
renderStandings = async function () {
  const s = await api("/api/standings");
  const panel = (name, teams) => `<section class="glass-panel team-view">
    <div class="eyebrow">${esc(name)} Division</div><h2>${esc(name)} Standings</h2>
    <div class="standing-row standing-labels"><span>#</span><span>Team</span><span>W</span><span>L</span><span>Diff</span></div>
    ${teams
      .map(
        (
          t,
          i,
        ) => `<div class="standing-row ${i < 4 ? `playoff-qualified playoff-seed-${i + 1}` : ""} ${i === 3 ? "playoff-cutoff" : ""}">
      <span class="standing-rank">${i + 1}</span><span class="standing-team">${teamBrand(t)}</span>
      <span>${t.wins}</span><span>${t.losses}</span><span>${t.differential >= 0 ? "+" : ""}${t.differential}</span></div>`,
      )
      .join("")}</section>`;
  $("#page-standings").innerHTML =
    `<div class="page-hero"><div><div class="eyebrow">Regular Season</div>
      <h1>Standings</h1><p class="muted">Wins → battle differential → head-to-head → team name. Top four qualify per division.</p></div></div>
    <div class="standings-grid">${panel("Kanto", s.A)}${panel("Johto", s.B)}</div>`;
};
