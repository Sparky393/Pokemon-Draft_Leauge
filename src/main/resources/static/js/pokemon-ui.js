/* Enhanced Pokemon card UI. Loaded after app.js so it can upgrade existing renderers without changing league APIs. */
const pokemonMetaCache = new Map();
const pokemonMetaRequests = new Map();
const POKEMON_TYPES = Object.freeze([
  "Bug",
  "Dark",
  "Dragon",
  "Electric",
  "Fairy",
  "Fighting",
  "Fire",
  "Flying",
  "Ghost",
  "Grass",
  "Ground",
  "Ice",
  "Normal",
  "Poison",
  "Psychic",
  "Rock",
  "Steel",
  "Water",
]);
function pokemonTypeFilter(prefix) {
  return `<div class="filter-field"><label for="${prefix}-type">Type</label><select id="${prefix}-type"><option value="all">All types</option>${POKEMON_TYPES.map(type => `<option value="${type.toLowerCase()}">${type}</option>`).join("")}</select></div>`;
}
// V6 invalidates old image lookups after correcting Rotom and Squawkabilly form slugs.
const POKE_CACHE_KEY = "draftLeaguePokemonMetaV6";
try {
  Object.entries(JSON.parse(localStorage.getItem(POKE_CACHE_KEY) || "{}"),).forEach(([k, v]) =>
    pokemonMetaCache.set(k, v)
  );
} catch {}
function pokeSlug(name) {
  const special = {
    "Raichu (Alolan)": "raichu-alola",
    "Ninetales (Alolan)": "ninetales-alola",
    "Persian (Alolan)": "persian-alola",
    "Arcanine (Hisuian)": "arcanine-hisui",
    "Slowbro (Galarian)": "slowbro-galar",
    "Tauros (Paldean Combat)": "tauros-paldea-combat-breed",
    "Tauros (Paldean Blaze)": "tauros-paldea-blaze-breed",
    "Tauros (Paldean Aqua)": "tauros-paldea-aqua-breed",
    "Typhlosion (Hisuian)": "typhlosion-hisui",
    "Slowking (Galarian)": "slowking-galar",
    "Samurott (Hisuian)": "samurott-hisui",
    "Zoroark (Hisuian)": "zoroark-hisui",
    "Stunfisk (Galarian)": "stunfisk-galar",
    "Goodra (Hisuian)": "goodra-hisui",
    "Avalugg (Hisuian)": "avalugg-hisui",
    "Decidueye (Hisuian)": "decidueye-hisui",
    "Indeedee (Male)": "indeedee-male",
    "Indeedee (Female)": "indeedee-female",
    "Basculegion (Male)": "basculegion-male",
    "Basculegion (Female)": "basculegion-female",
    "Toxtricity (Amped)": "toxtricity-amped",
    "Toxtricity (Low Key)": "toxtricity-low-key",
    Squawkabilly: "squawkabilly-blue-plumage",
    "Floette (Eternal Flower)": "floette-eternal",
    // These stay ONE league/draft entry. The form below is only the representative PokeAPI image/stats form.
    Pyroar: "pyroar-male",
    Maushold: "maushold-family-of-four",
    Aegislash: "aegislash-shield",
    Gourgeist: "gourgeist-average",
    Mimikyu: "mimikyu-disguised",
    Morpeko: "morpeko-full-belly",
    Palafin: "palafin-zero",
    Meowstic: "meowstic-male",
    "Meowstic (Male)": "meowstic-male",
    "Meowstic (Female)": "meowstic-female",
    Lycanroc: "lycanroc-midday",
    "Lycanroc (Midday)": "lycanroc-midday",
    "Lycanroc (Midnight)": "lycanroc-midnight",
    "Lycanroc (Dusk)": "lycanroc-dusk",
    Rotom: "rotom",
    "Rotom Heat": "rotom-heat",
    "Rotom Wash": "rotom-wash",
    "Rotom Frost": "rotom-frost",
    "Rotom Fan": "rotom-fan",
    "Rotom Mow": "rotom-mow",
  };
  if (special[name]) return special[name];
  return name
    .toLowerCase()
    .replace(/[.'’]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}
function prettyPokeText(s) {
  return String(s || "")
    .split("-")
    .map((x) => (x ? x[0].toUpperCase() + x.slice(1) : ""))
    .join(" ");
}
async function fetchPokemonData(slug) {
  if (globalThis.leagueSession?.isEnded()) throw new Error("Session ended");
  const r = await fetch(`https://pokeapi.co/api/v2/pokemon/${slug}`);
  if (!r.ok) throw new Error("not found");
  return r.json();
}
async function getPokemonMeta(name) {
  if (pokemonMetaCache.has(name)) {
    const cached = pokemonMetaCache.get(name);
    if (cached?.image && Array.isArray(cached.types) && cached.types.length) return cached;
  }
  if (pokemonMetaRequests.has(name)) return pokemonMetaRequests.get(name);
  const request = loadPokemonMeta(name);
  pokemonMetaRequests.set(name, request);
  try {
    return await request;
  } finally {
    if (pokemonMetaRequests.get(name) === request) pokemonMetaRequests.delete(name);
  }
}
async function loadPokemonMeta(name) {
  try {
    let data;
    try {
      data = await fetchPokemonData(pokeSlug(name));
    } catch {
      if (!name.includes("(")) throw new Error("not found");
      data = await fetchPokemonData(pokeSlug(name.split(" (")[0]));
    }
    const meta = {
      image:
        data.sprites?.other?.["official-artwork"]?.front_default ||
        data.sprites?.other?.home?.front_default ||
        data.sprites?.front_default ||
        "",
      types: (data.types || []).map(t => prettyPokeText(t.type.name)),
      abilities: (data.abilities || []).map((a) => prettyPokeText(a.ability.name),),
      moves: (data.moves || []).map((m) => prettyPokeText(m.move.name)),
      stats: (data.stats || []).map((s) => ({
        name: prettyPokeText(s.stat.name),
        value: s.base_stat,
      })),
    };
    pokemonMetaCache.set(name, meta);
    try {
      localStorage.setItem(POKE_CACHE_KEY, JSON.stringify(Object.fromEntries(pokemonMetaCache)),);
    } catch {}
    return meta;
  } catch {
    const cached = pokemonMetaCache.get(name);
    return cached
      ? { ...cached, types: [] }
      : { image: "", types: [], abilities: [], moves: [], stats: [] };
  }
}
function pokemonPriceText(price) {
  return price == null ? "Price unavailable" : `$${price}`;
}
function pokemonCard(p, extra = "") {
  const id = p.pokemonId ?? p.id;
  return `<button type="button" class="pokemon-card ${extra}" data-pokemon-id="${id}" data-pokemon-name="${esc(p.name)}"><div class="pokemon-art"><div class="poke-loader">•••</div><img alt="${esc(p.name)}" loading="lazy"></div><div class="pokemon-card-body"><strong>${esc(p.name)}</strong>${p.price != null ? `<span class="price-badge">$${p.price}</span>` : ""}<div class="poke-meta muted"></div></div></button>`;
}
async function hydratePokemonCards(root = document) {
  const cards = [...root.querySelectorAll(".pokemon-card")];
  let cursor = 0;
  async function worker() {
    while (cursor < cards.length) {
      if (globalThis.leagueSession?.isEnded()) return;
      const card = cards[cursor++],
        m = await getPokemonMeta(card.dataset.pokemonName),
        img = card.querySelector("img"),
        loader = card.querySelector(".poke-loader");
      if (globalThis.leagueSession?.isEnded()) return;
      if (img && m.image) {
        img.src = m.image;
        img.classList.add("loaded");
      }
      if (loader) loader.remove();
      const line = card.querySelector(".poke-meta");
      if (line && m.abilities.length) line.textContent = m.abilities.slice(0, 2).join(" · ");
    }
  }
  await Promise.all(Array.from({ length: Math.min(10, cards.length) }, worker));
}
function advancedFilterBar(prefix) {
  return `<div class="pokemon-filters type-filter-bar"><div class="filter-field wide"><label>Pokémon</label><input id="${prefix}-search" placeholder="Search Pokémon..."></div><div class="filter-field"><label>Price</label><select id="${prefix}-price"><option value="name">Name A–Z</option><option value="low">Price Low → High</option><option value="high">Price High → Low</option></select></div>${pokemonTypeFilter(prefix)}<div class="filter-field"><label>Move</label><input id="${prefix}-move" list="${prefix}-move-list" placeholder="Type a move..."><datalist id="${prefix}-move-list"></datalist></div><div class="filter-field"><label>Ability</label><input id="${prefix}-ability" list="${prefix}-ability-list" placeholder="Type an ability..."><datalist id="${prefix}-ability-list"></datalist></div><button type="button" class="secondary clear-poke-filters" id="${prefix}-clear">Clear</button></div>`;
}
async function wireAdvancedFilters(prefix, pokemon, draw) {
  const names = [...pokemon];
  let filterRevision = 0;
  const apply = async () => {
    const revision = ++filterRevision;
    const q = $(`#${prefix}-search`).value.trim().toLowerCase(),
      move = $(`#${prefix}-move`).value.trim().toLowerCase(),
      ability = $(`#${prefix}-ability`).value.trim().toLowerCase(),
      sort = $(`#${prefix}-price`).value,
      type = $(`#${prefix}-type`).value;
    let rows = names.filter((p) => p.name.toLowerCase().includes(q));
    if (move || ability || type !== "all") {
      const tested = [];
      for (const p of rows) {
        const m = await getPokemonMeta(p.name);
        if (revision !== filterRevision) return;
        if (type !== "all" && !m.types?.some(t => t.toLowerCase() === type)) continue;
        if (move && !m.moves.some((x) => x.toLowerCase().includes(move))) continue;
        if (ability && !m.abilities.some((x) => x.toLowerCase().includes(ability))) continue;
        tested.push(p);
      }
      rows = tested;
    }
    rows.sort((a, b) =>
      sort === "low"
        ? a.price - b.price || a.name.localeCompare(b.name)
        : sort === "high"
          ? b.price - a.price || a.name.localeCompare(b.name)
          : a.name.localeCompare(b.name),
    );
    if (revision === filterRevision) draw(rows);
  };
  ["search", "move", "ability"].forEach((x) =>
    $(`#${prefix}-${x}`).addEventListener("input", apply),
  );
  $(`#${prefix}-price`).addEventListener("change", apply);
  $(`#${prefix}-type`).addEventListener("change", apply);
  $(`#${prefix}-clear`).onclick = () => {
    $(`#${prefix}-search`).value = "";
    $(`#${prefix}-move`).value = "";
    $(`#${prefix}-ability`).value = "";
    $(`#${prefix}-price`).value = "name";
    $(`#${prefix}-type`).value = "all";
    return apply();
  };
}
renderRosters = async function () {
  const data = await api("/api/rosters");
  $("#page-rosters").innerHTML =
    `<div class="section-title"><div><h1>Rosters</h1><p class="muted">Browse every team and its Pokémon.</p></div><select id="roster-team"><option value="all">All Teams</option>${data.map((r) => `<option value="${r.team.id}">${esc(r.team.name)}</option>`).join("")}</select></div><div id="roster-content"></div>`;
  const draw = () => {
    const wanted = $("#roster-team").value,
      rows = wanted === "all" ? data : data.filter((r) => String(r.team.id) === wanted);
    $("#roster-content").innerHTML = `<div class="roster-team-grid">${rows
      .map((r) => {
        const value = r.pokemon.reduce((s, p) => s + Number(p.price || 0), 0);
        return `<section class="card roster-panel"><div class="roster-heading"><div class="roster-title-wrap">${teamAvatar(r.team, "lg")}<div><span class="eyebrow">DIVISION ${esc(r.team.division)}</span><h2>${esc(r.team.name)}</h2><p class="muted">${esc(r.team.coachName)}</p></div></div><div class="roster-badges"><span class="pill">${r.pokemon.length}/10</span><span class="roster-value">$${value}/$100</span></div></div><div class="pokemon-card-grid roster-pokemon-grid">${r.pokemon.map((p) => pokemonCard(p)).join("") || '<div class="empty-state">No Pokémon drafted yet.</div>'}</div></section>`;
      })
      .join("")}</div>`;
    const root = $("#roster-content");
    hydratePokemonCards(root);
    root.querySelectorAll(".pokemon-card").forEach(
      (c) =>
        (c.onclick = () => {
          const p = data
            .flatMap((r) => r.pokemon)
            .find((x) => Number(x.pokemonId ?? x.id) === Number(c.dataset.pokemonId),);
          if (p && typeof showPokemonDetails === "function") showPokemonDetails(p);
        }),
    );
  };
  $("#roster-team").onchange = draw;
  draw();
};
