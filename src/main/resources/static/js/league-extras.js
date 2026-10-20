/* Optional in-site notifications, read-only downloads, and draft safeguards. */
(() => {
  let noticeCache = [];
  let lastNoticeKeys = new Set();
  let notifiedOnce = false;

  const sidebar = document.querySelector(".sidebar");
  if (!sidebar) return;
  const wrapper = document.createElement("div");
  wrapper.className = "league-tool-links";
  wrapper.innerHTML = `
    <button type="button" id="league-notices-toggle" class="secondary" aria-expanded="false">
      Notifications <span id="league-notice-count" class="league-notice-count" hidden>0</span>
    </button>
    <section id="league-notices-panel" class="league-tool-panel hidden" aria-label="League notifications">
      <h3>Coach Notifications</h3>
      <p class="muted small">Updates appear while you're signed into the website. Browser alerts require permission.</p>
      <div id="league-notices-list"><p class="muted">Checking notifications…</p></div>
      <button id="league-browser-alerts" type="button" class="secondary">Enable browser alerts</button>
    </section>
    `;
  sidebar.insertBefore(wrapper, document.querySelector("#logout-button"));

  const $notice = selector => wrapper.querySelector(selector);
  const noticePanel = $notice("#league-notices-panel");

  function toggle(panel, button) {
    const opened = panel.classList.contains("hidden");
    noticePanel.classList.add("hidden");
    $notice("#league-notices-toggle").setAttribute("aria-expanded","false");
    if (opened) {
      panel.classList.remove("hidden");
      button.setAttribute("aria-expanded","true");
    }
  }
  $notice("#league-notices-toggle").onclick = () => {
    toggle(noticePanel,$notice("#league-notices-toggle"));
    markRead();
  };
  $notice("#league-browser-alerts").onclick = async () => {
    if (!("Notification" in window)) return toast("Browser notifications aren't supported here.",true);
    if (Notification.permission === "granted") return toast("Browser notifications are already enabled.");
    const permission = await Notification.requestPermission();
    toast(permission==="granted" ? "Browser alerts enabled while this website is open." :
      "Browser alerts were not enabled.",permission!=="granted");
  };

  function readKey() {
    return "league-notice-seen-v1:" + (state.user?.username || "guest");
  }
  function seen() {
    try { return new Set(JSON.parse(localStorage.getItem(readKey()) || "[]")); }
    catch { return new Set(); }
  }
  function markRead() {
    const values = new Set([...seen(), ...noticeCache.map(n => n.id)]);
    try { localStorage.setItem(readKey(), JSON.stringify([...values].slice(-150))); } catch {}
    renderNotices();
  }
  function renderNotices() {
    const read = seen();
    const unread = noticeCache.filter(n=>!read.has(n.id)).length;
    const counter = $notice("#league-notice-count");
    counter.hidden = unread === 0; counter.textContent = String(unread);
    $notice("#league-notices-list").innerHTML = noticeCache.length
      ? noticeCache.map(n => `<button type="button" class="league-notice-item" data-page="${esc(n.page)}">
          <strong>${esc(n.title)}</strong><small>${esc(n.message)}</small></button>`).join("")
      : '<p class="muted small">No outstanding alerts right now.</p>';
    $notice("#league-notices-list").querySelectorAll("[data-page]").forEach(button=>{
      button.onclick=()=>{
        markRead();
        noticePanel.classList.add("hidden");
        showPage(button.dataset.page);
      };
    });
  }

  async function checkNotices() {
    if (!state.user || document.hidden) return;
    try {
      const next = await api("/api/notifications");
      if (!Array.isArray(next)) return;
      const old = lastNoticeKeys;
      const read = seen();
      noticeCache = next;
      renderNotices();
      if (notifiedOnce) for (const n of next) {
        if (old.has(n.id) || read.has(n.id)) continue;
        toast(n.title + " — " + n.message);
        if ("Notification" in window && Notification.permission === "granted")
          new Notification(n.title,{body:n.message,tag:"league-"+n.id});
      }
      lastNoticeKeys = new Set(next.map(n=>n.id));
      notifiedOnce = true;
    } catch (error) {
      // Notification polling should never interrupt drafting or lineups.
      console.warn("League notifications unavailable:",error.message);
    }
  }

  const enter = enterApp;
  enterApp = async function () {
    await enter();
    // Starting the read is optional and cannot prevent logging in.
    checkNotices();
  };
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) checkNotices();
  });
  window.setInterval(checkNotices, 120000);
  // Keep the active page centered in the swipeable mobile navigation rail.
  const nav = document.querySelector(".sidebar nav");
  nav?.addEventListener("click", event => {
    const button = event.target.closest("button[data-page]");
    if (!button) return;
    noticePanel.classList.add("hidden");
    if (window.matchMedia("(max-width: 800px)").matches) {
      button.scrollIntoView({block:"nearest", inline:"center", behavior:"smooth"});
    }
  });


  const render = renderDraft;
  renderDraft = async function () {
    await render();
    const root = document.querySelector("#page-draft");
    if (!root || !state.user) return;
    const d = await api("/api/draft");
    const header = document.createElement("div");
    header.className = "card draft-budget-panel";
    if (d.settings.draftFinalized) {
      header.innerHTML = '<strong>Official Draft Finalized</strong><p class="muted">All original draft picks are protected. Roster changes now occur through official league actions.</p>';
    } else if (d.currentTeam) {
      const owned = await api("/api/rosters/" + d.currentTeam.id);
      const current = owned.reduce((sum,mon)=>sum+Number(mon.price||0),0);
      const left = 100-current;
      header.innerHTML = `<strong>${esc(d.currentTeam.name)}: $${left} draft budget remaining</strong>
        <p class="muted">${owned.length}/10 Pokémon selected · $${current}/$100 used.
        Draft picks cannot exceed the $100 roster budget.</p><p class="muted" id="draft-cost-preview">
        Select a Pokémon to preview the remaining budget.</p>`;
      root.onclick = e => {
        const card = e.target.closest?.("[data-pokemon-id]");
        if (!card || !root.contains(card)) return;
        const mon = d.available.find(p=>Number(p.id)===Number(card.dataset.pokemonId));
        const status = root.querySelector("#draft-cost-preview");
        if (!mon || !status) return;
        const after = left-Number(mon.price);
        status.textContent = after < 0
          ? `${mon.name} costs $${mon.price}, which exceeds the $${left} remaining budget.`
          : `${mon.name} costs $${mon.price}. After this pick: $${after} remaining.`;
        status.classList.toggle("budget-error",after<0);
      };
    } else {
      header.innerHTML = '<strong>Draft board complete</strong><p class="muted">All 160 draft positions have been selected.</p>';
    }

    if (state.user.role === "MANAGER" && d.picks.length === 160 && !d.settings.draftFinalized) {
      const button = document.createElement("button");
      button.textContent="Finalize Official Draft";
      button.className="primary";
      button.onclick=async()=>{
        if (!confirm("Finalize all 160 picks? This permanently disables ordinary pick undo and prevents re-opening the draft. Ensure you have a database backup first."))return;
        try { await api("/api/manager/draft/finalize",{method:"POST"});toast("Draft finalized");await renderDraft(); }
        catch(e){toast(e.message,true);}
      };
      header.appendChild(button);
    }
    const board=root.querySelector(".live-draft-board");
    if(board)board.insertAdjacentElement("beforebegin",header);
    else root.insertAdjacentElement("afterbegin",header);
  };
})();
