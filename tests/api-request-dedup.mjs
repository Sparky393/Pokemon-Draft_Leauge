/** Test the actual browser API helper with controlled fetch completion, without a server. */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const calls = [];
const context = vm.createContext({
  structuredClone,
  fetch(url, options) {
    return new Promise((resolve, reject) =>
      calls.push({ url, options, resolve, reject }),
    );
  },
});
vm.runInContext(
  readFileSync("src/main/resources/static/js/api-fix.js", "utf8"),
  context,
);
const api = context.api;
function complete(call, value = { items: [1] }) {
  call.resolve({
    ok: true,
    status: 200,
    text: async () => JSON.stringify(value),
  });
}

const first = api("/api/teams"),
  second = api("/api/teams");
assert.equal(calls.length, 1, "Identical concurrent reads share a request");
complete(calls[0]);
const [a, b] = await Promise.all([first, second]);
a.items.push(2);
assert.deepEqual(
  b.items,
  [1],
  "Consumers receive independent response objects",
);

const later = api("/api/teams");
assert.equal(calls.length, 2, "Completed responses are never reused");
complete(calls[1]);
await later;

const oldRead = api("/api/settings");
const write = api("/api/manager/settings", {
  method: "PUT",
  body: { currentWeek: 2 },
});
const newRead = api("/api/settings");
assert.equal(calls.length, 5, "A mutation separates old and new reads");
complete(calls[2], { currentWeek: 1 });
complete(calls[3], { currentWeek: 2 });
complete(calls[4], { currentWeek: 2 });
await Promise.all([oldRead, write, newRead]);

const rejected = api("/api/rosters");
calls[5].reject(new Error("offline"));
await assert.rejects(rejected, /Could not reach/);
const retry = api("/api/rosters");
assert.equal(calls.length, 7, "A failed request can be retried");
complete(calls[6]);
await retry;

const explicitA = api("/api/teams", { headers: { "X-Test": "a" } });
const explicitB = api("/api/teams", { headers: { "X-Test": "b" } });
assert.equal(
  calls.length,
  9,
  "Reads with explicit request options are not coalesced",
);
complete(calls[7]);
complete(calls[8]);
await Promise.all([explicitA, explicitB]);
console.log(
  "API request regression checks passed: deduplication, isolation, freshness, retry, options.",
);
