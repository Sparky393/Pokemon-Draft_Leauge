/* Shared team identity, used across the active season and roster views. */
function lookupTeam(ref) {
  if (ref && typeof ref === "object") return ref;
  return state.teams.find(t => typeof ref === "number"
    ? Number(t.id) === ref : t.name === ref) || null;
}

function teamAvatar(ref, size = "sm") {
  const team = lookupTeam(ref);
  if (!team) return "";
  const name = team.name || "Team";
  const initials = name.trim().split(/\s+/).slice(0,2).map(w => w[0] || "").join("").toUpperCase();
  const image = team.logoUrl
    ? `<img src="${esc(team.logoUrl)}" alt="" loading="lazy" decoding="async">`
    : `<span class="team-avatar-fallback">${esc(initials)}</span>`;
  return `<span class="team-avatar team-avatar--${size}" aria-hidden="true">${image}</span>`;
}

function teamBrand(ref, size = "sm") {
  const team = lookupTeam(ref);
  if (!team) return `<span class="team-brand-text">${esc(typeof ref === "string" ? ref : "TBD")}</span>`;
  return `<span class="team-brand">${teamAvatar(team, size)}<span class="team-brand-text">${esc(team.name)}</span></span>`;
}

function refreshTeamIdentity(team) {
  state.teams = state.teams.map(t => Number(t.id) === Number(team.id) ? team : t);
  const sidebar = document.querySelector("#whoami");
  if (sidebar && state.user && state.user.teamId) {
    sidebar.innerHTML = `<div class="sidebar-identity">${teamAvatar(state.user.teamId, "md")}<div><strong>${esc(state.user.username)}</strong><small>${teamBrand(state.user.teamId)}</small></div></div>`;
  }
}
