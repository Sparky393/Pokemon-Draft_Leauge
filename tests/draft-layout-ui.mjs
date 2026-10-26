/** Actual selection/navigation/scroll handlers with DOM doubles; no server or database. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

class Element {
  listeners = new Map();
  attrs = {};
  disabled = false;
  hidden = false;
  value = "";
  textContent = "";
  dataset = {};
  classes = new Set();
  classList = {
    toggle: (name, on) => {
      if (on) this.classes.add(name);
      else this.classes.delete(name);
    },
    contains:name => this.classes.has(name)
  };
  addEventListener(name, fn) {
    this.listeners.set(name, fn);
  }
  removeEventListener(name, fn) {
    if (this.listeners.get(name) === fn) this.listeners.delete(name);
  }
  setAttribute(name, value) {
    this.attrs[name] = value;
  }
  focus() {
    this.focused = true;
  }
  set innerHTML(html) {
    this.html = html;
  }
  get innerHTML() {
    return this.html || "";
  }
}
const nodes = new Map();
const grid = new Element();
Object.defineProperty(grid, "innerHTML", {
  set(html) {
    this.cards = [...html.matchAll(/data-pokemon-id="([^"]+)"/g)].map((m) => {
      const card = new Element();
      card.dataset.pokemonId = m[1];
      return card;
    });
  },
  get() {
    return "";
  }
});
grid.querySelectorAll = () => grid.cards || [];
nodes.set("#draft-grid", grid);
const page = new Element();
Object.defineProperty(page, "innerHTML", {
  set(html) {
    this.html = html;
    for (const [, id] of html.matchAll(/id="([^"]+)"/g))
      if (id !== "draft-grid") nodes.set(`#${id}`, new Element());
    nodes.get("#make-pick").disabled = /<button id="make-pick"[^>]*disabled/.test(html);
  },
  get() {
    return this.html;
  }
});
nodes.set("#page-draft", page);
const storage = new Map(),
  posts = [],
  details = [];
let refreshes = 0,
  failNext = false;
const context = vm.createContext({
  state: { user: { role: "COACH", teamId: 8 } },
  $:selector => nodes.get(selector),
  localStorage: {
    getItem:key => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, value)
  },
  esc:value => String(value ?? ""),
  teamBrand:team => team.name,
  document: { querySelector: () => null, addEventListener: () => {} },
  api: async (url, options) => {
    posts.push({ url, options });
    if (failNext) {
      failNext = false;
      throw Error("Retryable error");
    }
  },
  toast: () => {},
  setInterval: () => {},
  addEventListener: () => {},
  removeEventListener: () => {},
});
context.window = context;
vm.runInContext(readFileSync("src/main/resources/static/js/pokemon-ui.js", "utf8"), context);
vm.runInContext(readFileSync("src/main/resources/static/js/final-ui.js", "utf8"), context);
const render = context.renderDraft;
context.hydratePokemonCards = () => {};
context.wireFinalFilters = () => {};
context.showPokemonDetails =p => details.push(p);
vm.runInContext(readFileSync("src/main/resources/static/js/draft-live.js", "utf8"), context);
const renderBoard = context.renderDraft;
context.renderDraft = async () => {
  refreshes++;
};
const snapshot = {
  settings: { draftOpen: true, draftFinalized: false, snakeDraft: true },
  currentTeam: { id: 8, name: "Team 8" },
  picks: [],
  available: [{ id: 445, name: "Garchomp", price: 18 }]
};

await render(snapshot);
assert.equal(nodes.get("#make-pick").disabled, true, "A pick requires an explicit selection");
grid.cards[0].onclick();
assert.equal(nodes.get("#draft-action-selection").textContent, "Selected: Garchomp · $18");
assert.equal(nodes.get("#make-pick").disabled, false);
assert.equal(details.at(-1).price, 18);
await nodes.get("#make-pick").onclick();
assert.equal(posts.at(-1).url, "/api/draft/pick");
assert.equal(posts.at(-1).options.body.pokemonId, 445);
assert.equal(refreshes, 1);

await render(snapshot);
grid.cards[0].onclick();
failNext = true;
await nodes.get("#make-pick").onclick();
assert.equal(nodes.get("#make-pick").disabled, false, "Failed saves permit retry");
for (const [role, teamId, open, finalized] of [
  ["GUEST", null, true, false],
  ["COACH", 9, true, false],
  ["MANAGER", null, false, false],
  ["MANAGER", null, true, true]
]) {
  context.state.user = { role, teamId };
  await render({
    ...snapshot,
    settings: { ...snapshot.settings, draftOpen: open, draftFinalized: finalized }
  });
  grid.cards[0].onclick();
  assert.equal(nodes.get("#make-pick").disabled, true);
  const before = posts.length;
  await nodes.get("#make-pick").onclick();
  assert.equal(posts.length, before);
}
assert.equal(context.pokemonPriceText(null), "Price unavailable");
assert.equal(context.pokemonPriceText(0), "$0");
assert.equal(
  context.draftBoardPokemon({ pokemonId: 445, pokemonName: "Garchomp", price: 18 }).price,
  18
);
assert.equal(context.draftBoardPokemon({ pokemonId: 1, pokemonName: "Free", price: 0 }).price, 0);

// Budgets use public draft picks, never the separate FAAB balance or roster prices.
const teamPicks = (prices, teamId = 8) => prices.map((price, i) => ({
  teamId, price, overallPick: i + 1, pokemonId: i + 1, pokemonName: `Pokemon ${i + 1}`
}));
assert.equal(context.draftTeamBudget([], 8).text, "$100 left · $10/slot");
assert.equal(context.draftTeamBudget(teamPicks([10, 16, 14, 18]), 8).text, "$42 left · $7/slot");
assert.equal(context.draftTeamBudget(teamPicks([10, 10, 10, 10]), 8).text, "$60 left · $10/slot");
assert.equal(context.draftTeamBudget(teamPicks([10, 10, 10, 10, 10, 8]), "8").text, "$42 left · $10.5/slot");
assert.equal(context.draftTeamBudget(teamPicks([30, 30, 20]), 8).text, "$20 left · $2.86/slot");
assert.equal(context.draftTeamBudget(teamPicks([0]), 8).text, "$100 left · $11.11/slot");
assert.equal(context.draftTeamBudget(teamPicks([100]), 8).text, "$0 left · $0/slot");
assert.equal(context.draftTeamBudget(teamPicks(Array(10).fill(10)), 8).text, "$0 left · Roster full");
assert.equal(context.draftTeamBudget(teamPicks(Array(10).fill(9)), 8).text, "$10 left · Roster full");
assert.equal(context.draftTeamBudget(teamPicks([50], 9), 8).text, "$100 left · $10/slot");
for (const price of [null, undefined, NaN]) {
  assert.equal(context.draftTeamBudget(teamPicks([price]), 8).text, "Budget unavailable");
}
let boardHtml = "";
page.children = [];
page.querySelector = () => null;
page.insertAdjacentHTML = (_, html) => { boardHtml = html; };
context.document.querySelector = (selector) => selector === "#page-draft" ? page : null;
context.pokemonCard = () => "<div>Pokemon</div>";
context.state.teams = [{ id: 8, name: "Team Eight", draftPosition: 1, faBudget: 3 },
  { id: 9, name: "Team Nine", draftPosition: 2, faBudget: 3 }];
for (const role of ["COACH", "MANAGER", "GUEST"]) {
  context.state.user = { role, teamId: role === "COACH" ? 8 : null };
  const before = posts.length;
  const draft = { ...snapshot, picks: teamPicks([18]) };
  const saved = JSON.stringify(draft);
  await renderBoard(draft);
  assert.match(boardHtml, /class="draft-team-budget"[^>]*>\$82 left · \$9.11\/slot<\/span>/);
  assert.match(boardHtml, /\$100 left · \$10\/slot/);
  assert.match(boardHtml, /9 open slots/);
  await renderBoard({ ...draft, picks: teamPicks([18, 10]) });
  assert.match(boardHtml, /\$72 left · \$9\/slot/);
  await renderBoard({ ...draft, picks: [] });
  assert.match(boardHtml, /\$100 left · \$10\/slot/);
  assert.equal(JSON.stringify(draft), saved, "Displaying budgets must not modify picks");
  assert.equal(posts.length, before, "Budget headers must not make extra API requests");
}
context.document.querySelector = () => null;
console.log("PASS: draft budgets, averages, free picks, full rosters, missing prices, team isolation, all roles, pick/undo rerenders; no extra requests or mutations");

const scroller = new Element(),
  position = new Element(),
  left = new Element(),
  right = new Element();
Object.assign(scroller, { scrollWidth: 2600, clientWidth: 1000, scrollLeft: 0 });
left.dataset.draftScroll = "-1";
right.dataset.draftScroll = "1";
scroller.scrollBy = ({ left: amount }) => {
  scroller.scrollLeft = Math.max(
    0,
    Math.min(scroller.scrollWidth-scroller.clientWidth, scroller.scrollLeft + amount)
  );
  scroller.listeners.get("scroll")();
};
let observer;
context.ResizeObserver = class {
  constructor(callback) {
    this.callback = callback;
    observer = this;
  }
  observe() {}
  disconnect() {
    this.disconnected = true;
  }
};
const cleanup = context.wireDraftBoardScrolling({
  querySelector:selector =>selector === ".draft-board-scroll" ? scroller : position,
  querySelectorAll: () => [left, right]
});
assert.equal(position.max, "1600");
assert.equal(left.disabled, true);
position.value = "1200";
position.listeners.get("input")();
assert.equal(scroller.scrollLeft, 1200);
left.listeners.get("click")();
assert.equal(scroller.scrollLeft, 400);
assert.equal(position.value, "400");
scroller.clientWidth = 1800;
observer.callback();
assert.equal(position.max, "800", "Sidebar resizing recalculates the slider");
cleanup();
assert.equal(observer.disconnected, true);
assert.equal(scroller.listeners.size, 0);
assert.equal(position.listeners.size, 0);

const app = new Element(),
  close = new Element(),
  open = new Element();
const navigation = vm.createContext({
  document: {
    querySelector:s => ({ "#app": app, "#sidebar-toggle": close, "#sidebar-open": open })[s]
  },
  localStorage: {
    getItem:key => storage.get(key),
    setItem: (key, value) => storage.set(key, value)
  }
});
const source = readFileSync("src/main/resources/static/js/navigation-ui.js", "utf8");
vm.runInContext(source, navigation);
assert.equal(open.hidden, true);
close.listeners.get("click")();
assert.equal(app.classes.has("sidebar-collapsed"), true);
assert.equal(open.hidden, false);
assert.equal(open.focused, true);
assert.equal(close.attrs["aria-expanded"], "false");
vm.runInContext(source, navigation);
assert.equal(open.hidden, false, "Collapsed preference survives reload");
open.listeners.get("click")();
assert.equal(app.classes.has("sidebar-collapsed"), false);
assert.equal(open.hidden, true);
assert.equal(close.focused, true);
console.log(
  "PASS: selected name/price beside button, submission/retry, coach/guest/closed/finalized guards, drafted prices including zero/unknown, scroll syncing/resize/cleanup, navigation collapse/persistence/focus"
);
