/* Form-specific fixes loaded last so they override older generic mappings/details UI. */
const FORM_SLUG_OVERRIDES = {
  "Rotom Heat": "rotom-heat",
  "Rotom Wash": "rotom-wash",
  "Rotom Frost": "rotom-frost",
  "Rotom Fan": "rotom-fan",
  "Rotom Mow": "rotom-mow",
  "Meowstic (Male)": "meowstic-male",
  "Meowstic (Female)": "meowstic-female",
  Squawkabilly: "squawkabilly-blue-plumage",
  Palafin: "palafin-zero",
};
const oldPokeSlug = pokeSlug;
pokeSlug = function (name) {
  return FORM_SLUG_OVERRIDES[name] || oldPokeSlug(name);
};

/* Do not reuse stale metadata saved while form slugs were wrong. */
[
  "Rotom Heat",
  "Rotom Wash",
  "Rotom Frost",
  "Rotom Fan",
  "Rotom Mow",
  "Squawkabilly",
  "Palafin",
  "Meowstic (Male)",
  "Meowstic (Female)",
].forEach((n) => pokemonMetaCache.delete(n));

/* Mega Meowstic is one Mega form usable by either male or female Meowstic. */
const MEOWSTIC_MEGA_SLUGS = {
  "Meowstic (Male)": "meowstic-mega",
  "Meowstic (Female)": "meowstic-mega",
};
const Z_MEGA_SLUGS = {
  Lucario: "lucario-mega-z",
  Garchomp: "garchomp-mega-z",
  Absol: "absol-mega-z",
};
const PALAFIN_HERO_SLUG = "palafin-hero";

// Verified against Pokémon Showdown's data/pokedex.ts and PokeAPI's form IDs.
// Display-only forms: these never create separate draft slots or database rows.
const MEGA_Y_METADATA = {
  "charizard-mega-y": {
    types: ["Fire", "Flying"],
    abilities: ["Drought"],
    image:
      "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/10035.png",
    values: [78, 104, 78, 159, 115, 100],
  },
  "raichu-mega-y": {
    types: ["Electric"],
    abilities: ["No Guard"],
    image:
      "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/10305.png",
    values: [60, 100, 55, 160, 80, 130],
  },
};

/* PokeAPI may lag newly released Champions/Z-A Mega forms, so keep a local fallback for Mega Meowstic. */
const oldFetchMetaBySlug = fetchMetaBySlug;
fetchMetaBySlug = async function (slug) {
  const known = MEGA_Y_METADATA[slug];
  if (known)
    return {
      image: known.image,
      types: [...known.types],
      abilities: [...known.abilities],
      moves: [],
      stats: ["Hp", "Attack", "Defense", "Special Attack", "Special Defense", "Speed"].map(
        (name, i) => ({ name, value: known.values[i] })
      ),
    };
  const found = await oldFetchMetaBySlug(slug);
  if (found) return found;
  if (slug === "meowstic-mega")
    return {
      image: "https://play.pokemonshowdown.com/sprites/ani/meowstic-mega.gif",
      abilities: ["Trace"],
      moves: [],
      stats: [
        { name: "Hp", value: 74 },
        { name: "Attack", value: 48 },
        { name: "Defense", value: 76 },
        { name: "Special Attack", value: 143 },
        { name: "Special Defense", value: 101 },
        { name: "Speed", value: 124 },
      ],
    };
  return null;
};

/* M cycles base → X → Y → base; Z Mega and Hero keep their existing controls. */
showPokemonDetails = function (p) {
  getPokemonFullMeta(p.name).then((base) => {
    if (globalThis.leagueSession?.isEnded()) return;
    document.querySelector(".pokemon-detail-overlay")?.remove();
    const allForms = megaFormsFor(p.name);
    const normalMega =
      MEOWSTIC_MEGA_SLUGS[p.name] || allForms.find((slug) => !slug.endsWith("-mega-z")) || null;
    const yMega = allForms.find((slug) => slug.endsWith("-mega-y")) || null;
    const zMega = Z_MEGA_SLUGS[p.name] || null;
    const hasHero = p.name === "Palafin";
    const overlay = document.createElement("div");
    overlay.className = "pokemon-detail-overlay";
    let active = "base";
    let paintRevision = 0;

    const paint = async () => {
      const revision = ++paintRevision;
      let meta = base,
        title = p.name,
        badge = "",
        eyebrow = "Pokémon Details";
      if (active === "mega" && normalMega) {
        const mm = await fetchMetaBySlug(normalMega);
        if (mm) {
          meta = mm;
          title = p.name.startsWith("Meowstic") ? "Mega Meowstic" : megaLabel(normalMega);
          badge = "MEGA";
          eyebrow = "Mega Evolution";
        } else {
          toast("Mega data is not available from the Pokémon data service yet.", true,);
          active = "base";
        }
      } else if (active === "y" && yMega) {
        const ym = await fetchMetaBySlug(yMega);
        if (ym) {
          meta = ym;
          title = megaLabel(yMega);
          badge = "MEGA Y";
          eyebrow = "Mega Evolution";
        } else {
          toast("Mega Y data is not available from the Pokémon data service yet.", true);
          active = "base";
        }
      } else if (active === "z" && zMega) {
        const zm = await fetchMetaBySlug(zMega);
        if (zm) {
          meta = zm;
          title = megaLabel(zMega);
          badge = "Z MEGA";
          eyebrow = "Z Mega Evolution";
        } else {
          toast("Z Mega data is not available from the Pokémon data service yet.", true,);
          active = "base";
        }
      } else if (active === "hero" && hasHero) {
        const hm = await fetchMetaBySlug(PALAFIN_HERO_SLUG);
        if (hm) {
          meta = hm;
          title = "Palafin Hero Form";
          badge = "HERO";
          eyebrow = "Zero to Hero";
        } else {
          toast("Palafin Hero data is not available from the Pokémon data service yet.", true,);
          active = "base";
        }
      }

      if (globalThis.leagueSession?.isEnded()) {
        overlay.remove();
        return;
      }
      if (revision !== paintRevision) return;
      const controls = [];
      if (normalMega)
        controls.push(
          `<button class="mega-toggle ${active === "mega" || active === "y" ? "active" : ""}" data-form="mega" title="${yMega ? (active === "mega" ? "View Mega Y Evolution" : active === "y" ? "Return to base form" : "View Mega X Evolution") : active === "mega" ? "Return to base form" : "View Mega Evolution"}" aria-label="Toggle Mega Evolution"><span class="mega-symbol">M</span></button>`,
        );
      if (zMega)
        controls.push(
          `<button class="mega-toggle z-mega-toggle ${active === "z" ? "active" : ""}" data-form="z" title="${active === "z" ? "Return to base form" : "View Z Mega Evolution"}" aria-label="Toggle Z Mega Evolution"><span class="mega-symbol">Z</span></button>`,
        );
      if (hasHero)
        controls.push(
          `<button class="mega-toggle hero-toggle ${active === "hero" ? "active" : ""}" data-form="hero" title="${active === "hero" ? "Return to Zero Form" : "Zero to Hero"}" aria-label="Toggle Palafin Hero Form"><span class="mega-symbol">H</span></button>`,
        );

      overlay.innerHTML = `<div class="pokemon-detail-card"><button class="detail-close" aria-label="Close">×</button>${controls.join("")}<div class="detail-art">${meta.image ? `<img src="${meta.image}" alt="${esc(title)}">` : ""}</div><div class="detail-info"><div class="eyebrow">${eyebrow}</div><h2>${esc(title)}</h2><div class="detail-badges"><span>${pokemonPriceText(p.price)}</span><span>BST ${bstOf(meta) || "—"}</span>${badge ? `<span>${badge}</span>` : ""}</div>${meta.types?.length ? `<p><strong>Type:</strong> ${meta.types.map(esc).join(" / ")}</p>` : ""}<p><strong>Abilities:</strong> ${meta.abilities?.length ? meta.abilities.join(" · ") : "Unknown"}</p><div class="stat-list">${(meta.stats || []).map((s) => `<div><span>${esc(s.name)}</span><strong>${s.value}</strong></div>`).join("") || '<p class="muted">Stats unavailable.</p>'}</div></div></div>`;
      overlay.querySelector(".detail-close").onclick = () => overlay.remove();
      overlay.querySelectorAll("[data-form]").forEach(
        (btn) =>
          (btn.onclick = async (e) => {
            e.stopPropagation();
            const requested = btn.dataset.form;
            active =
              requested === "mega" && yMega
                ? active === "mega"
                  ? "y"
                  : active === "y"
                    ? "base"
                    : "mega"
                : active === requested
                  ? "base"
                  : requested;
            await paint();
          }),
      );
    };

    document.body.appendChild(overlay);
    overlay.onclick = (e) => {
      if (e.target === overlay) overlay.remove();
    };
    paint();
  });
};
