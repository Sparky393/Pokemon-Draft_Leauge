import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const context = vm.createContext({
  localStorage: { getItem: () => null, setItem() {} },
  fetch: async () => ({ ok: true, json: async () => ({
    sprites: { front_default: "emolga.png" },
    types: [{ type: { name: "electric" } }],
    moves: [{ move: { name: "tailwind" } }],
  }) }),
});
for (const file of ["champions-learnsets.js", "pokemon-ui.js", "final-ui.js"])
  vm.runInContext(readFileSync(`src/main/resources/static/js/${file}`, "utf8"), context);

const pool = readFileSync("src/main/java/com/pokemondraft/leagueportal/config/DataInitializer.java", "utf8")
  .split("private List<String> championsPool()")[1];
for (const [, name] of pool.matchAll(/"([^"]+)"/g))
  assert.ok(context.championsMovesForSlug(context.pokeSlug(name)).length, `Missing learnset: ${name}`);
for (const meta of [await context.getPokemonMeta("Emolga"), await context.fetchMetaBySlug("emolga")]) {
  assert.ok(!meta.moves.includes("Tailwind"), "Cross-game Tailwind must not leak into Champions");
  assert.ok(meta.moves.includes("Electroweb"));
}
assert.ok(context.championsMovesForSlug("whimsicott").includes("Tailwind"));
assert.ok(context.championsMovesForSlug("charizard-mega-y").includes("Heat Wave"));
assert.equal(context.championsMovesForSlug("unknown").length, 0);
assert.notDeepEqual(context.championsMovesForSlug("indeedee-male"), context.championsMovesForSlug("indeedee-female"));
console.log("PASS: Champions coverage for every draft species; Emolga regression, valid Tailwind, Mega inheritance, distinct female learnsets, unknown species.");
