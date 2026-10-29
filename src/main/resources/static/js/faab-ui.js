/* Complete FAAB add/drop UI. Loaded after the base UI. */
(() => {
  const pokemonNames = new Map();

  async function loadPokemonNames() {
    const all = await api("/api/pokemon");
    pokemonNames.clear();
    all.forEach((p) => pokemonNames.set(Number(p.id), p.name));
  }

  window.loadFaabPokemonNames = loadPokemonNames;

  function pokemonNameForDisplay(id) {
    if (id == null) return "—";
    return pokemonNames.get(Number(id)) || "Unknown Pokémon";
  }

  function bidPokemonName(b, kind) {
    const name = b[kind === "wanted" ? "wantedPokemonName" : "dropPokemonName"];
    const id = b[kind === "wanted" ? "wantedPokemonId" : "dropPokemonId"];
    if (name) return name;
    if (id == null) return "—";
    const cached = pokemonNames.get(Number(id));
    return cached || `Pokémon #${id} (not found)`;
  }

  window.bidTable = function (list) {
    const manager = state.user.role === "MANAGER";
    return `<div class="card"><h2>${manager ? "All FAAB Bids" : "My FAAB Bids"}</h2>
      <div class="table-wrap"><table><thead><tr><th>Team</th><th>Free Agent</th><th>Drop If Won</th><th>Bid</th><th>Status</th>${manager ? "" : "<th>Action</th>"}</tr></thead>
      <tbody>${list.map((b) => `<tr><td>${esc(teamName(b.teamId))}</td><td>${esc(bidPokemonName(b, "wanted"))}</td><td>${esc(bidPokemonName(b, "drop"))}</td><td>$${b.amount}</td><td>${esc(b.status)}</td>${manager ? "" : `<td>${b.status === "PENDING" ? `<button type="button" class="secondary cancel-faab-bid" data-id="${b.id}">Withdraw</button>` : "—"}</td>`}</tr>`).join("") || `<tr><td colspan="${manager ? 5 : 6}">No bids yet</td></tr>`}</tbody></table></div></div>`;
  };

  window.renderBids = async function () {
    await refreshBase();
    await loadPokemonNames();
    const list = await api("/api/free-agency/bids");
    const free = await api("/api/pokemon/free-agents");

    if (state.user.role === "MANAGER") {
      const pending = list.filter((b) => b.status === "PENDING").length;
      $("#fa-content").innerHTML =
        `<div class="card"><div class="section-title"><div><h2>Blind FAAB Processing</h2><p class="muted">${pending} pending bid${pending === 1 ? "" : "s"}. Processing awards each free agent to the highest valid bid; worse record wins ties; identical records use a random draw.</p></div><button id="process-bids" class="primary" ${pending && state.settings?.waiverWindowOpen ? "" : "disabled"}>Process Pending Bids</button></div></div>${bidTable(list)}`;
      const button = $("#process-bids");
      if (button)
        button.addEventListener("click", async () => {
          if (
            !confirm(
              `Process ${pending} pending FAAB bid${pending === 1 ? "" : "s"}? Winners will be added, selected drops will become free agents, and FAAB will be deducted.`,
            )
          )
            return;
          button.disabled = true;
          try {
            const r = await api("/api/manager/free-agency/process", {
              method: "POST",
            });
            toast(
              `FAAB processed: ${r.winners} winning bid${r.winners === 1 ? "" : "s"}`,
            );
            if (r.wheels?.length)
              alert(
                "Wheel tiebreak results:\\n" +
                  r.wheels
                    .map(
                      (w) =>
                        w.pokemon +
                        ": " +
                        w.teams.join(" / ") +
                        " → " +
                        w.winner,
                    )
                    .join("\\n"),
              );
            await renderBids();
          } catch (e) {
            toast(e.message, true);
            button.disabled = false;
          }
        });
      return;
    }

    const myRoster = await api(`/api/rosters/${state.user.teamId}`);
    const me = state.teams.find((t) => t.id === state.user.teamId);
    const complete = myRoster.length === 10;
    const pendingBids = list.filter((b) => b.status === "PENDING");
    const reserved = pendingBids.reduce((sum, b) => sum + b.amount, 0);
    const budget = Math.max(0, Math.min(3, me?.faBudget ?? 0) - reserved);
    const open = !!state.settings?.waiverWindowOpen;
    $("#fa-content").innerHTML =
      `<div class="card"><h2>Submit Blind FAAB Bid</h2>
      <p>FAAB available to bid: <strong>${budget}/$3</strong> · Roster: <strong>${myRoster.length}/10</strong></p>
      <p class="muted">Two waiver periods in Week 4 after all Week 3 results. Each bid is $1–$3; combined pending bids cannot exceed available FAAB. Teams must have exactly 10 Pokémon, and the free agent cannot cost more than the Pokémon dropped.</p>
      <div class="toolbar">
        <label>Free agent<select id="bid-wanted"><option value="">Choose free agent</option>${free.map((p) => `<option value="${p.id}">${esc(p.name)} — $${p.price}</option>`).join("")}</select></label>
        <label>Drop if bid wins<select id="bid-drop"><option value="">Choose Pokemon to drop</option>${myRoster.map((p) => `<option value="${p.pokemonId}">${esc(p.name)} — $${p.price}</option>`).join("")}</select></label>
        <label>FAAB bid<input id="bid-amount" type="number" min="1" max="${budget}" value="${budget > 0 ? 1 : 0}" placeholder="1"></label>
        <button id="submit-bid" class="primary" ${!open || pendingBids.length >= 3 || budget <= 0 || free.length === 0 || !complete ? "disabled" : ""}>Submit Blind Bid</button>
      </div>
      <p id="bid-preview" class="small muted" role="status" aria-live="polite"></p>
      <p id="bid-error" class="form-error hidden" role="alert"></p></div>${bidTable(list)}`;

    $("#fa-content .cancel-faab-bid").forEach((button) => {
      button.disabled = !open;
      button.onclick = async () => {
        if (
          !confirm(
            "Withdraw this pending FAAB bid? It will no longer be eligible to win.",
          )
        )
          return;
        button.disabled = true;
        try {
          await api(`/api/free-agency/bids/${button.dataset.id}`, {
            method: "DELETE",
          });
          toast("Pending bid withdrawn");
          await renderBids();
        } catch (error) {
          toast(error.message, true);
          button.disabled = false;
        }
      };
    });

    const preview = () => {
      const wanted = free.find((p) => p.id === Number($("#bid-wanted").value));
      const drop = myRoster.find(
        (p) => p.pokemonId === Number($("#bid-drop").value),
      );
      const amount = Number($("#bid-amount").value);
      const current = myRoster.reduce((sum, p) => sum + (p.price || 0), 0);
      const after = current - (drop?.price || 0) + (wanted?.price || 0);
      let reason = "";
      if (!open)
        reason =
          state.settings?.waiverClosedReason ||
          "Week 4 waivers are currently closed.";
      else if (!complete)
        reason = `Your roster has ${myRoster.length}/10 Pokémon. You need exactly 10 to bid.`;
      else if (pendingBids.length >= 3)
        reason = "You already have the maximum of 3 pending bids.";
      else if (budget <= 0)
        reason = "You have no available FAAB after existing bids.";
      else if (!free.length) reason = "No free agents are currently available.";
      else if (!wanted) reason = "Choose the free agent you want to acquire.";
      else if (!drop)
        reason = "Choose a Pokémon from your roster to drop if the bid wins.";
      else if (pendingBids.some((b) => Number(b.wantedPokemonId) === wanted.id))
        reason = `You already have a pending bid for ${wanted.name}.`;
      else if (
        pendingBids.some((b) => Number(b.dropPokemonId) === drop.pokemonId)
      )
        reason = `${drop.name} is already selected as the drop in another pending bid.`;
      else if (wanted.price > drop.price)
        reason = `${wanted.name} costs $${wanted.price}, but ${drop.name} costs $${drop.price}. The free agent cannot cost more than your drop.`;
      else if (after > 100)
        reason = `This move would put your roster at $${after}/$100.`;
      else if (!Number.isInteger(amount) || amount < 1 || amount > budget)
        reason = `Enter a whole-dollar bid between $1 and $${budget}.`;
      $("#submit-bid").disabled = !!reason;
      $("#bid-preview").textContent =
        reason ||
        `Valid bid: ${wanted.name} ($${wanted.price}) for ${drop.name} ($${drop.price}). Roster if won: $${after}/$100.`;
      $("#bid-preview").classList.toggle("budget-error", !!reason);
    };
    $("#bid-wanted").addEventListener("change", preview);
    $("#bid-drop").addEventListener("change", preview);
    $("#bid-amount").addEventListener("input", preview);
    preview();
    $("#submit-bid").addEventListener("click", async () => {
      const wanted = Number($("#bid-wanted").value),
        dropValue = $("#bid-drop").value,
        drop = dropValue ? Number(dropValue) : null,
        amount = Number($("#bid-amount").value);
      if (!complete)
        return toast(
          "Your roster must have exactly 10 Pokemon before using free agency",
          true,
        );
      if (!wanted) return toast("Choose a free agent", true);
      if (!drop) return toast("Choose who to drop if you win", true);
      if (!Number.isInteger(amount) || amount < 1 || amount > budget)
        return toast(`Bid must be between $1 and $${budget}`, true);
      $("#bid-error").classList.add("hidden");
      try {
        await api("/api/free-agency/bids", {
          method: "POST",
          body: { wantedPokemonId: wanted, dropPokemonId: drop, amount },
        });
        toast("Blind bid submitted");
        await renderBids();
      } catch (e) {
        $("#bid-error").textContent = e.message;
        $("#bid-error").classList.remove("hidden");
        toast(e.message, true);
      }
    });
  };
})();
