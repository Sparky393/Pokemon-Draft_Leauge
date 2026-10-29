/** Offline UI regression: real form renderer, stubbed DOM/data service, no database. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const externalLookups = [];
let overlay;
const base = {
  image: "base.png",
  abilities: ["Static"],
  moves: [],
  stats: [{ name: "Hp", value: 60 }]
};
const context = vm.createContext({
  pokemonMetaCache: new Map(),
  pokemonPriceText: (price) =>price == null ? "Price unavailable" : `$${price}`,
  pokeSlug: (name) => name.toLowerCase(),
  prettyPokeText: (s) =>
    s
      .split("-")
      .map((x) => x[0]?.toUpperCase() + x.slice(1))
      .join(" "),
  getPokemonMeta: async () => base,
  championsMovesForSlug: () => [],
  fetch: async (url) => {
    externalLookups.push(url);
    return {
      ok: true,
      json: async () => ({
        sprites: { front_default: "x.png" },
        abilities: [{ ability: { name: "unchanged-x-ability" } }],
        moves: [],
        stats: [{ stat: { name: "hp" }, base_stat: 60 }],
      })
    };
  },
  esc: (s) => String(s ?? ""),
  toast: (message) => {
    throw new Error(message);
  },
  document: {
    querySelector: () => overlay,
    createElement: () => ({
      html: "",
      buttons: [],
      set innerHTML(value) {
        this.html = value;
        this.buttons = [...value.matchAll(/data-form="([^"]+)"/g)].map((m) => ({
          dataset: { form: m[1] }
        }));
      },
      querySelector: () => ({}),
      querySelectorAll() {
        return this.buttons;
      },
      remove() {
        overlay = null;
      },
    }),
    body: {
      appendChild: (node) => {
        overlay = node;
      }
    },
  },
});
for (const file of ["final-ui.js", "form-ui-fixes.js"])
  vm.runInContext(readFileSync(`src/main/resources/static/js/${file}`, "utf8"), context);

const plain = (value) => JSON.parse(JSON.stringify(value));
const expected = [
  {
    name: "Charizard",
    slug: "charizard-mega-y",
    types: ["Fire", "Flying"],
    ability: "Drought",
    stats: [78, 104, 78, 159, 115, 100],
    bst: 634,
    sprite: "10035.png"
  },
  {
    name: "Raichu",
    slug: "raichu-mega-y",
    types: ["Electric"],
    ability: "No Guard",
    stats: [60, 100, 55, 160, 80, 130],
    bst: 585,
    sprite: "10305.png"
  },
];
for (const form of expected) {
  const lookups = externalLookups.length;
  const meta = await context.fetchMetaBySlug(form.slug);
  assert.deepEqual(plain(meta.types), form.types);
  assert.deepEqual(plain(meta.abilities), [form.ability]);
  assert.deepEqual(plain(meta.stats.map((s) => s.value)), form.stats);
  assert.equal(context.bstOf(meta), form.bst);
  assert.ok(meta.image.endsWith(form.sprite));
  assert.equal(externalLookups.length, lookups, "Y metadata must work without remote lookups");
  assert.ok(context.pokemonSearchNames({ name: form.name }).includes(`Mega ${form.name} Y`));
  assert.ok(context.pokemonFilterAbilities({ name: form.name }, base).includes(form.ability));

  context.showPokemonDetails({ id: 42, name: form.name, price: 10 });
  await new Promise(setImmediate);
  assert.ok(overlay.html.includes(`<h2>${form.name}</h2>`));
  assert.deepEqual(
    overlay.buttons.map((b) => b.dataset.form),
    ["mega"],
    "Only the M control is needed for X/Y"
  );
  const clickM = async () => overlay.buttons[0].onclick({ stopPropagation() {} });
  await clickM();
  assert.ok(overlay.html.includes(`<h2>Mega ${form.name} X</h2>`), "First M click displays X");
  assert.ok(overlay.html.includes("Unchanged X Ability"), "X still uses its original data path");
  await clickM();
  assert.ok(overlay.html.includes(`<h2>Mega ${form.name} Y</h2>`), "Second M click displays Y");
  assert.ok(overlay.html.includes(form.ability));
  assert.ok(overlay.html.includes(`BST ${form.bst}`));
  assert.ok(overlay.html.includes(form.types.join(" / ")));
  await clickM();
  assert.ok(overlay.html.includes(`<h2>${form.name}</h2>`), "Third M click restores base");
}

context.showPokemonDetails({ id: 43, name: "Lucario", price: 10 });
await new Promise(setImmediate);
assert.deepEqual(
  overlay.buttons.map((b) => b.dataset.form),
  ["mega", "z"],
  "Z control is preserved"
);
context.showPokemonDetails({ id: 44, name: "Palafin", price: 10 });
await new Promise(setImmediate);
assert.deepEqual(
  overlay.buttons.map((b) => b.dataset.form),
  ["hero"],
  "Hero control is preserved"
);
console.log(
  "PASS: both Y forms, sprites, stats, types, abilities, search, M cycling, original X data path, Z and Hero controls"
);
