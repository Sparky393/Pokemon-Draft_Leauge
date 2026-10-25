/** Fake wall clock, multiple tabs, real idle controller/API helper/draft poller. No server. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
const IDLE = 180000;
const source = readFileSync(
  "src/main/resources/static/js/session-idle.js",
  "utf8",
);
const apiSource = readFileSync(
  "src/main/resources/static/js/api-fix.js",
  "utf8",
);
const user = { id: 1, username: "coach1", role: "COACH", teamId: 1 };
const flush = async () => {
  for (let i = 0; i < 12; i++) await Promise.resolve();
};
class Node {
  listeners = new Map();
  addEventListener(name, fn) { this.listeners.set(name, fn); }
  setAttribute(name, value) { this[name] = value; }
  insertAdjacentElement() {}
  click() { return this.listeners.get("click")?.({}); }
  classes = new Set();
  value = "secret";
  hidden = true;
  inert = false;
  textContent = "";
  classList = {
    add: (n) => this.classes.add(n),
    remove: (n) => this.classes.delete(n),
    contains: (n) => this.classes.has(n),
  };
}
function world() {
  let now = 1000,
    nextTimer = 1;
  const storage = new Map(),
    tabs = [],
    timers = new Map(),
    calls = [];
  const response = (value) => ({
    status: 200,
    ok: true,
    text: async () => JSON.stringify(value),
    json: async () => structuredClone(value),
  });
  const schedule = (tab, fn, delay, interval = 0) => {
    const id = nextTimer++;
    timers.set(id, { tab, fn, at: now + delay, interval });
    return id;
  };
  const w = {
    storage,
    tabs,
    calls,
    failLogout: false,
    hold: new Set(),
    pending: [],
    get now() {
      return now;
    },
    async advance(ms, run = true) {
      const end = now + ms;
      if (run) {
        while (true) {
          const due = [...timers]
            .filter(([, t]) => t.at <= end)
            .sort((a, b) => a[1].at - b[1].at)[0];
          if (!due) break;
          const [id, t] = due;
          timers.delete(id);
          now = t.at;
          if (t.interval) timers.set(id, { ...t, at: now + t.interval });
          await t.fn();
          await flush();
        }
      }
      now = end;
      await flush();
    },
    tab() {
      const listeners = new Map(),
        nodes = new Map(
          [
            "#app",
            "#login-screen",
            "#login-username",
            "#login-password",
            "#session-idle-message",
            "#session-refresh-status",
          ].map((id) => [id, new Node()]),
        );
      const t = { nodes, listeners, id: tabs.length };
      tabs.push(t);
      t.emit = (name, event = {}) => {
        for (const fn of [...(listeners.get(name) || [])]) fn(event);
      };
      const add = (name, fn) => {
        if (!listeners.has(name)) listeners.set(name, new Set());
        listeners.get(name).add(fn);
      };
      const document = {
        hidden: false,
        querySelector: (selector) => {
          if (selector.startsWith("#") && !nodes.has(selector)) nodes.set(selector, new Node());
          return nodes.get(selector) || null;
        },
        createElement: () => new Node(),
        querySelectorAll: () => [],
        addEventListener: add,
        removeEventListener: (name, fn) => listeners.get(name)?.delete(fn),
      };
      const c = vm.createContext({
        state: {
          user: { ...user },
          teams: [{ id: 1 }],
          settings: { currentWeek: 1 },
          page: "draft",
        },
        document,
        localStorage: {
          getItem: (key) => storage.get(key) || null,
          setItem: (key, value) => {
            storage.set(key, value);
            for (const other of tabs)
              if (other !== t) other.emit("storage", { key });
          },
        },
        Date: class extends Date {
          static now() {
            return now;
          }
        },
        Math,
        AbortController,
        structuredClone,
        setTimeout: (fn, delay) => schedule(t, fn, delay),
        clearTimeout: (id) => timers.delete(id),
        setInterval: (fn, delay) => schedule(t, fn, delay, delay),
        clearInterval: (id) => timers.delete(id),
        addEventListener: add,
        removeEventListener: (name, fn) => listeners.get(name)?.delete(fn),
        console: { warn() {}, log() {} },
        fetch: (url, options = {}) => {
          const call = { url, options, tab: t.id, at: now };
          calls.push(call);
          if (url === "/api/auth/logout")
            return w.failLogout
              ? Promise.reject(Error("Offline"))
              : Promise.resolve(response(null));
          if (w.hold.has(url))
            return new Promise((resolve, reject) => {
              w.pending.push({
                call,
                resolve: (value) => resolve(response(value)),
                reject,
              });
              options.signal?.addEventListener(
                "abort",
                () => reject(Error("Aborted")),
                { once: true },
              );
            });
          if (url === "/api/auth/login" || url === "/api/auth/me") return Promise.resolve(response(user));
          if (url === "/api/auth/guest") return Promise.resolve(response({id:null,username:"Guest",role:"GUEST",teamId:null}));
          const status = {
            pickCount: 0,
            lastPickId: null,
            currentTeamId: 1,
            draftOpen: true,
            draftFinalized: false,
            snakeDraft: true,
          };
          return Promise.resolve(
            response(
              url === "/api/draft/status"
                ? status
                : url === "/api/notifications"
                  ? []
                  : {},
            ),
          );
        },
      });
      c.window = c;
      t.c = c;
      t.document = document;
      t.state = c.state;
      vm.runInContext(source, c);
      vm.runInContext(apiSource, c);
      t.begin = (fresh = true) => {
        t.state.user = { ...user };
        return c.leagueSession.begin(t.state.user, fresh);
      };
      t.input = (name = "pointerdown", isTrusted = true) =>
        t.emit(name, { isTrusted });
      return t;
    },
  };
  return w;
}
const checks = [];
async function test(name, fn) {
  await fn();
  checks.push(name);
  console.log("PASS:", name);
}

await test("poll requests never extend three-minute inactivity; exactly one logout; no later API traffic", async () => {
  const w = world(),
    t = w.tab();
  assert.equal(t.begin(), true);
  for (let i = 0; i < 5; i++) {
    await w.advance(30000);
    await t.c.api("/api/notifications");
  }
  await w.advance(30000);
  assert.equal(t.state.user, null);
  assert.equal(t.nodes.get("#app").classes.has("hidden"), true);
  assert.equal(t.nodes.get("#login-password").value, "");
  assert.equal(w.calls.filter((c) => c.url === "/api/auth/logout").length, 1);
  const count = w.calls.length;
  await assert.rejects(t.c.api("/api/draft/status"));
  await w.advance(IDLE * 3);
  await t.c.leagueSession.logout();
  assert.equal(w.calls.length, count);
});

await test("real keyboard/mouse/wheel/touch activity extends the deadline; synthetic events and programmatic scroll do not", async () => {
  for (const event of [
    "keydown",
    "pointermove",
    "wheel",
    "touchstart",
    "touchmove",
  ]) {
    const w = world(),
      t = w.tab();
    t.begin();
    await w.advance(170000);
    t.input(event);
    await w.advance(170000);
    assert.ok(t.state.user);
    await w.advance(10000);
    assert.equal(t.state.user, null);
  }
  for (const [event, trusted] of [
    ["pointerdown", false],
    ["scroll", true],
  ]) {
    const w = world(),
      t = w.tab();
    t.begin();
    await w.advance(170000);
    t.input(event, trusted);
    await w.advance(10000);
    assert.equal(t.state.user, null);
  }
});

await test("hidden tabs make no reads; returning in time refreshes once and blocks actions until current data loads", async () => {
  const w = world(),
    t = w.tab();
  t.begin();
  let release,
    refreshes = 0;
  t.c.leagueSession.setRefresh(() => {
    refreshes++;
    return new Promise((resolve) => {
      release = resolve;
    });
  });
  t.document.hidden = true;
  t.emit("visibilitychange");
  assert.equal(t.c.leagueSession.canPoll(), false);
  await assert.rejects(t.c.api("/api/notifications"));
  assert.equal(w.calls.length, 0);
  await w.advance(60000);
  t.document.hidden = false;
  t.emit("visibilitychange");
  t.emit("focus");
  await flush();
  assert.equal(refreshes, 1);
  assert.equal(t.nodes.get("#app").inert, true);
  await assert.rejects(
    t.c.api("/api/draft/pick", { method: "POST", body: { pokemonId: 1 } }),
  );
  assert.equal(w.calls.length, 0);
  release();
  await flush();
  assert.equal(t.nodes.get("#app").inert, false);
  assert.equal(t.c.leagueSession.canPoll(), true);
  await t.c.api("/api/draft/pick", { method: "POST", body: { pokemonId: 1 } });
  assert.equal(w.calls.length, 1);
});

await test("a suspended tab checks elapsed wall time before refreshing or accepting input", async () => {
  const w = world(),
    t = w.tab();
  t.begin();
  let refreshes = 0;
  t.c.leagueSession.setRefresh(async () => {
    refreshes++;
  });
  t.document.hidden = true;
  t.emit("visibilitychange");
  await w.advance(IDLE + 1, false);
  t.document.hidden = false;
  t.emit("focus");
  t.input();
  t.emit("visibilitychange");
  await flush();
  assert.equal(refreshes, 0);
  assert.equal(t.state.user, null);
  assert.equal(w.calls.length, 1);
  assert.equal(w.calls[0].url, "/api/auth/logout");
});

await test("hiding or logging out cancels outstanding reads; late responses cannot restore old UI state", async () => {
  const w = world(),
    t = w.tab();
  t.begin();
  w.hold.add("/api/teams");
  const result = t.c.api("/api/teams");
  const rejected = assert.rejects(result);
  t.document.hidden = true;
  t.emit("visibilitychange");
  await rejected;
  assert.equal(w.calls[0].options.signal.aborted, true);
  t.document.hidden = false;
  await t.c.leagueSession.logout();
  assert.equal(t.state.user, null);
  assert.equal(t.c.leagueSession.canRestore(), false);
});

await test("already-sent mutations are never aborted or retried when the session expires", async () => {
  const w = world(),
    t = w.tab();
  t.begin();
  w.hold.add("/api/manager/results/1");
  const save = t.c.api("/api/manager/results/1", {
    method: "POST",
    body: { homeScore: 2, awayScore: 1 },
  });
  const rejected = assert.rejects(save);
  await w.advance(IDLE);
  assert.equal(w.calls[0].options.signal, undefined);
  w.pending[0].resolve({ saved: true });
  await rejected;
  assert.equal(
    w.calls.filter((c) => c.url === "/api/manager/results/1").length,
    1,
  );
});

await test("activity in another tab keeps the shared session alive; logout propagates without duplicate logout requests", async () => {
  const w = world(),
    a = w.tab();
  a.begin();
  const b = w.tab();
  assert.equal(b.begin(false), true);
  await w.advance(170000);
  b.input();
  await w.advance(20000);
  assert.ok(a.state.user);
  assert.ok(b.state.user);
  await a.c.leagueSession.logout();
  await flush();
  assert.equal(b.state.user, null);
  assert.equal(w.calls.filter((c) => c.url === "/api/auth/logout").length, 1);
});

await test("a new login in another tab stops stale permissions without logging out the new session", async () => {
  const w = world(),
    a = w.tab();
  a.begin();
  const b = w.tab();
  b.begin(true);
  await flush();
  assert.equal(a.state.user, null);
  assert.ok(b.state.user);
  assert.equal(w.calls.length, 0);
});

await test("expired/reloaded pages cannot revive a session; genuine fresh login or guest entry can start again", async () => {
  const w = world(),
    a = w.tab();
  a.begin();
  await w.advance(IDLE + 1, false);
  const b = w.tab();
  assert.equal(b.c.leagueSession.canRestore(), false);
  assert.equal(w.calls.length, 1);
  await b.c.api("/api/auth/login", {
    method: "POST",
    body: { username: "coach1", password: "dummy" },
  });
  assert.equal(b.begin(true), true);
  assert.equal(b.c.leagueSession.canPoll(), true);
  await b.c.leagueSession.logout();
  const count = w.calls.length;
  const c = w.tab();
  assert.equal(c.c.leagueSession.canRestore(), false);
  assert.equal(w.calls.length, count);
  await c.c.api("/api/auth/guest", { method: "POST" });
  c.state.user = { id: null, username: "Guest", role: "GUEST", teamId: null };
  assert.equal(c.c.leagueSession.begin(c.state.user, true), true);
});

await test("offline logout stays stopped and never retries automatically; refresh failures sign out safely", async () => {
  const w = world(),
    t = w.tab();
  t.begin();
  w.failLogout = true;
  await w.advance(IDLE);
  await flush();
  await w.advance(IDLE * 2);
  assert.equal(w.calls.length, 1);
  assert.equal(t.c.leagueSession.canPoll(), false);
  const v = world(),
    tab = v.tab();
  tab.begin();
  tab.c.leagueSession.setRefresh(async () => {
    throw Error("Offline");
  });
  tab.document.hidden = true;
  tab.emit("visibilitychange");
  tab.document.hidden = false;
  tab.emit("visibilitychange");
  await flush();
  assert.equal(tab.state.user, null);
  assert.equal(tab.nodes.get("#app").inert, false);
});

await test("actual five-second draft poller stops at timeout and while hidden, and active polling never counts as input", async () => {
  const w = world(),
    t = w.tab();
  t.begin();
  t.c.renderDraft = async () => {};
  vm.runInContext(
    readFileSync("src/main/resources/static/js/draft-live.js", "utf8"),
    t.c,
  );
  await w.advance(15000);
  assert.equal(w.calls.filter((c) => c.url === "/api/draft/status").length, 3);
  t.document.hidden = true;
  t.emit("visibilitychange");
  const count = w.calls.length;
  await w.advance(30000);
  assert.equal(w.calls.length, count);
  t.c.leagueSession.setRefresh(async () => {});
  t.document.hidden = false;
  t.emit("visibilitychange");
  await flush();
  await w.advance(IDLE);
  assert.equal(t.state.user, null);
  const after = w.calls.length;
  await w.advance(IDLE);
  assert.equal(w.calls.length, after);
  assert.equal(w.calls.filter((c) => c.url === "/api/auth/logout").length, 1);
});

await test("actual notification handler makes no requests when hidden or expired", async () => {
  const w = world(),
    t = w.tab();
  t.begin();
  const text = readFileSync(
    "src/main/resources/static/js/league-extras.js",
    "utf8",
  );
  const handler = text.slice(
    text.indexOf("  async function checkNotices()"),
    text.indexOf("  const enter = enterApp;"),
  );
  Object.assign(t.c, {
    checkingNotices: false,
    noticeCache: [],
    lastNoticeKeys: new Set(),
    notifiedOnce: false,
    seen: () => new Set(),
    renderNotices: () => {},
    toast: () => {},
  });
  vm.runInContext(handler, t.c);
  await t.c.checkNotices();
  assert.equal(w.calls.length, 1);
  t.document.hidden = true;
  t.emit("visibilitychange");
  await t.c.checkNotices();
  assert.equal(w.calls.length, 1);
  await w.advance(IDLE);
  await t.c.checkNotices();
  assert.equal(w.calls.length, 2);
  assert.equal(w.calls[1].url, "/api/auth/logout");
});

await test("new Pokémon metadata requests stop after logout", async () => {
  const w = world(),
    t = w.tab();
  t.begin();
  await t.c.leagueSession.logout();
  const count = w.calls.length;
  vm.runInContext(
    readFileSync("src/main/resources/static/js/pokemon-ui.js", "utf8"),
    t.c,
  );
  await assert.rejects(t.c.fetchPokemonData("garchomp"));
  await t.c.getPokemonMeta("Garchomp");
  assert.equal(w.calls.length, count);
});

await test("real app boot, login, guest entry and manual logout use the idle lifecycle without an automatic reload", async () => {
  const w = world(), t = w.tab();
  const app = readFileSync("src/main/resources/static/js/app.js", "utf8").replace(/\nboot\(\);\s*$/, "\n");
  vm.runInContext(app, t.c);
  const state = vm.runInContext("state", t.c);
  let entries = 0;
  t.c.enterApp = async () => { entries++; };
  await t.c.boot();
  assert.equal(entries, 1);
  assert.equal(state.user.role, "COACH");
  assert.equal(t.c.leagueSession.canPoll(), true);
  await w.advance(IDLE);
  assert.equal(state.user, null);
  const count = w.calls.length;
  await t.c.boot();
  assert.equal(w.calls.length, count, "Signed-out boot does not probe auth/me again");
  t.nodes.get("#login-username").value = "coach1";
  t.nodes.get("#login-password").value = "dummy";
  await t.nodes.get("#login-button").click();
  assert.equal(entries, 2);
  assert.equal(state.user.role, "COACH");
  assert.equal(t.c.leagueSession.canPoll(), true);
  await t.nodes.get("#logout-button").click();
  assert.equal(state.user, null);
  await t.nodes.get("#guest-button").click();
  assert.equal(entries, 3);
  assert.equal(state.user.role, "GUEST");
  assert.equal(t.c.leagueSession.canPoll(), true);
  await t.nodes.get("#logout-button").click();
  assert.equal(state.user, null);
});

console.log(
  `PASS: ${checks.length} inactivity/session integration scenarios; all network activity mocked`,
);
