/** Execute real playoff renderer and event handlers with a small DOM double; no browser/database. */
import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";
import vm from "node:vm";

const { scenarios } = JSON.parse(readFileSync("target/playoff-simulation-snapshots.json", "utf8"));
assert.equal(scenarios.length, 18, "Run PlayoffSimulationTest first");
const seasonSource = readFileSync("src/main/resources/static/js/season-ui.js", "utf8");
const brandingSource = readFileSync("src/main/resources/static/js/team-branding.js", "utf8");
const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[c]);
const attr = (tag, name) => tag.match(new RegExp(`${name}="([^"]*)"`))?.[1];

class PlayoffDom {
  html = "";
  nodes = [];
  set innerHTML(value) {
    this.html = value;
    this.nodes = [...value.matchAll(/<(input|button)\b[^>]*>/g)].map(([tag, kind]) => ({
      kind, classes: (attr(tag, "class") || "").split(/\s+/), value: attr(tag, "value") || "",
      dataset: { id: attr(tag, "data-id"), week: attr(tag, "data-week") }, onclick: null,
    }));
  }
  querySelectorAll(selector) { return this.nodes.filter((node) => node.classes.includes(selector.slice(1))); }
  querySelector(selector) {
    const [, className, id] = selector.match(/^\.([\w-]+)\[data-id="([^"]+)"\]$/) || [];
    return this.nodes.find((node) => node.classes.includes(className) && node.dataset.id === id);
  }
}

let screenChecks = 0, scoreSaves = 0;
for (const role of ["MANAGER", "COACH", "GUEST"]) {
  for (const scenario of scenarios) {
    let step = 0, locks = {};
    const root = new PlayoffDom(), notices = [], posts = [];
    const snapshot = () => scenario.snapshots[step];
    const context = vm.createContext({
      state: { user: { role }, teams: snapshot().teams }, esc: escapeHtml,
      $: (selector) => { assert.equal(selector, "#page-playoffs"); return root; },
      toast: (message, error) => notices.push({ message, error }), confirm: () => false,
      api: async (url, options = {}) => {
        if (url === "/api/playoffs") return structuredClone(snapshot().bracket);
        if (url === "/api/weeks/locks") return locks;
        assert.equal(role, "MANAGER");
        assert.equal(options.method, "POST");
        const result = scenario.snapshots[step + 1].result;
        assert.equal(url, `/api/manager/results/${result.id}`);
        assert.deepEqual(JSON.parse(JSON.stringify(options.body)), { homeScore: result.homeScore, awayScore: result.awayScore });
        posts.push(result);
        step++;
        return null;
      },
    });
    vm.runInContext(brandingSource, context);
    vm.runInContext(seasonSource, context);
    const assertScreen = () => {
      const actual = [...root.html.matchAll(/<div class="playoff-game ([^"]*)">([\s\S]*?)(?=<div class="playoff-game |$)/g)].map(([, classes, card]) => {
        const [, home, score, away] = card.match(/<div class="season-versus"><strong>([\s\S]*?)<\/strong><span>(.*?)<\/span><strong>([\s\S]*?)<\/strong><\/div>/);
        const teamText = (html) => html.match(/<span class="team-brand-text">([^<]*)<\/span>/)[1];
        return { label: card.match(/<div class="playoff-game-label">([^<]*)<\/div>/)[1],
          home: teamText(home), away: teamText(away), score, awaiting: classes.includes("awaiting") };
      });
      assert.deepEqual(actual, snapshot().bracket.games.map((game) => ({ label: `${game.division} · ${game.round}`,
        home: game.homeTeam, away: game.awayTeam, score: game.played ? `${game.homeScore} – ${game.awayScore}` : "vs", awaiting: !game.ready })),
      `scenario=${scenario.seed}, mask=${scenario.outcomeMask}, step=${step}, role=${role}`);
      assert.match(root.html, /Kanto: 1 vs 4 and 2 vs 3\. Johto: 1 vs 4 and 2 vs 3\./);
      assert.equal((root.html.match(/class="card playoff-stage"/g) || []).length, 3);
      const editable = snapshot().bracket.games.filter((g) => g.ready && !locks[g.week]);
      assert.equal(root.querySelectorAll(".save-result").length, role === "MANAGER" ? editable.length : 0);
      screenChecks++;
    };

    await context.renderPlayoffs();
    assertScreen();
    step = 1;
    context.state.teams = snapshot().teams;
    await context.renderPlayoffs();
    assertScreen();
    if (role === "MANAGER") {
      const id = snapshot().bracket.games[0].id;
      root.querySelector(`.score-home[data-id="${id}"]`).value = "3";
      root.querySelector(`.score-away[data-id="${id}"]`).value = "0";
      await root.querySelector(`.save-result[data-id="${id}"]`).onclick();
      assert.equal(posts.length, 0);
      assert.equal(step, 1);
      assert.equal(notices.at(-1).error, true);
      assert.match(notices.at(-1).message, /Playoff scores must be/);
    }
    for (let next = 2; next < scenario.snapshots.length; next++) {
      if (role === "MANAGER") {
        const result = scenario.snapshots[next].result;
        root.querySelector(`.score-home[data-id="${result.id}"]`).value = String(result.homeScore);
        root.querySelector(`.score-away[data-id="${result.id}"]`).value = String(result.awayScore);
        await root.querySelector(`.save-result[data-id="${result.id}"]`).onclick();
        assert.equal(notices.at(-1).message, "Result saved");
      } else {
        step = next;
        await context.renderPlayoffs();
      }
      assert.equal(step, next);
      assertScreen();
    }
    assert.equal(posts.length, role === "MANAGER" ? 7 : 0);
    scoreSaves += posts.length;
    locks = { 14: true, 15: true, 16: true };
    await context.renderPlayoffs();
    assertScreen();
    assert.equal(root.querySelectorAll(".clear-result").length, 0, "Locked rounds cannot be cleared");
  }
}
console.log(`PASS: ${screenChecks} frontend render/permission checks across manager, coach and guest; ${scoreSaves} actual score-save handler executions; invalid scores blocked; locked rounds protected`);

// Standalone interactive preview, using the same renderer and backend-generated snapshots.
const cssFiles = [...readFileSync("src/main/resources/static/index.html", "utf8").matchAll(/href="\/css\/([^?]+)\?/g)].map((m) => m[1]);
const css = cssFiles.map((name) => readFileSync(`src/main/resources/static/css/${name}`, "utf8")).join("\n");
const preview = `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Playoff simulation preview</title><style>${css}
body{display:block;padding:16px}.simulation-controls{position:relative;z-index:100;max-width:1200px;margin:0 auto 16px}#page-playoffs{max-width:1200px;margin:auto}#preview-status{overflow-wrap:anywhere}</style>
<div class="card simulation-controls"><h1>Playoff simulation</h1><p>Temporary mock league only. No accounts, server, or database connections.</p><div class="toolbar"><label>Scenario<select id="scenario"></select></label><label>Stage<select id="stage"></select></label><label>View<select id="role"><option>MANAGER</option><option>GUEST</option><option>COACH</option></select></label><button id="next" class="primary">Next stage</button><button id="save-next" class="secondary">Save next simulated score</button></div><p id="preview-status" class="muted"></p></div><section id="page-playoffs"></section>
<script>
const scenarios=${JSON.stringify(scenarios).replaceAll("<", "\\u003c")};
const state={user:{role:'MANAGER'},teams:[]};const $=s=>document.querySelector(s);const esc=${escapeHtml.toString()};
let scenarioIndex=0,step=0;const current=()=>scenarios[scenarioIndex].snapshots[step];
function toast(message){$('#preview-status').textContent=message;}
async function api(url,options={}){if(url==='/api/playoffs')return current().bracket;if(url==='/api/weeks/locks')return {};const next=scenarios[scenarioIndex].snapshots[step+1];if(state.user.role!=='MANAGER'||!next?.result||url!=='/api/manager/results/'+next.result.id||options.method!=='POST'||options.body.homeScore!==next.result.homeScore||options.body.awayScore!==next.result.awayScore)throw Error('Use Save next simulated score for this fixed simulation, or select a different stage.');step++;syncSelectors();return null;}
${brandingSource}
${seasonSource}
const stageNames=['Before regular season ends','Quarterfinals seeded','Kanto QF 1 complete','Kanto QF 2 complete','Johto QF 1 complete','All quarterfinals complete','Kanto semifinal complete','Finalists confirmed','Championship complete'];
$('#scenario').innerHTML=scenarios.map((s,i)=>'<option value="'+i+'">Seed '+s.seed+' / outcomes '+s.outcomeMask+'</option>').join('');
$('#stage').innerHTML=stageNames.map((s,i)=>'<option value="'+i+'">'+s+'</option>').join('');
function syncSelectors(){$('#stage').value=String(step);$('#next').disabled=step===8;$('#save-next').disabled=step<1||step===8||state.user.role!=='MANAGER';}
async function redraw(){state.teams=current().teams;syncSelectors();await renderPlayoffs();toast(stageNames[step]+' · seed '+scenarios[scenarioIndex].seed+' · outcome mask '+scenarios[scenarioIndex].outcomeMask);}
$('#scenario').onchange=()=>{scenarioIndex=Number($('#scenario').value);step=0;redraw();};$('#stage').onchange=()=>{step=Number($('#stage').value);redraw();};$('#role').onchange=()=>{state.user.role=$('#role').value;redraw();};$('#next').onclick=()=>{step=Math.min(8,step+1);redraw();};$('#save-next').onclick=()=>{const r=scenarios[scenarioIndex].snapshots[step+1].result;$('.score-home[data-id="'+r.id+'"]').value=r.homeScore;$('.score-away[data-id="'+r.id+'"]').value=r.awayScore;$('.save-result[data-id="'+r.id+'"]').click();};redraw();
</script></html>`;
writeFileSync("target/playoff-simulation-preview.html", preview);
console.log("Created target/playoff-simulation-preview.html: standalone interactive bracket with 18 simulated playoffs and nine stages each");
