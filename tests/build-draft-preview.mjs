/** Bundle actual HTML/CSS/JS with a strictly local fixture fetch adapter for review. */
import {readFileSync,writeFileSync,mkdirSync} from "node:fs";
import {installDraftPreview} from "./draft-preview-fixture.mjs";
const root="src/main/resources/static/";
let html=readFileSync(root+"index.html","utf8");
html=html.replace(/<link rel="stylesheet" href="\/([^?]+)\?[^\"]+"\s*\/>/g,(_,path)=>`<style>${readFileSync(root+path,"utf8")}</style>`);
const escapeScript=s=>s.replaceAll("</script","<\\/script");
html=html.replace(/<script src="\/([^?]+)\?[^\"]+"><\/script>/g,(_,path)=>{
  let source=readFileSync(root+path,"utf8");
  if(path==="js/app.js")source=source.replace(/\nboot\(\);\s*$/, "\n");
  return `<script>${escapeScript(source)}</script>`;
});
html=html.replace('<script>',`<script>(${escapeScript(installDraftPreview.toString())})();</script>\n<script>`);
html=html.replace('<main class="content">',`<main class="content"><div class="card preview-tools"><strong>Local draft layout preview</strong><p class="muted">Simulated league only. No production database or account changes. Pokémon artwork loads from PokeAPI's image repository.</p><div class="toolbar"><label>View as <select id="preview-role"><option value="COACH">Coach on the clock</option><option value="MANAGER">Commissioner</option><option value="GUEST">Guest</option></select></label><button id="preview-toggle-open" type="button" class="secondary">Toggle draft open / closed</button></div></div>`);
html=html.replace('</body>',`<script>document.querySelector('#preview-role').onchange=e=>setDraftPreviewRole(e.target.value);document.querySelector('#preview-toggle-open').onclick=()=>toggleDraftPreviewOpen();boot().then(()=>showPage('draft'));</script></body>`);
mkdirSync("target",{recursive:true});
writeFileSync("target/draft-layout-preview.html",html);
console.log("Created target/draft-layout-preview.html — actual site code/styles with mock data; no server required");
