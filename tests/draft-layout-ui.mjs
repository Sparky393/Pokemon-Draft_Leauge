/** Actual selection/navigation/scroll handlers with DOM doubles; no server or database. */
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import vm from "node:vm";

class Element {
  listeners=new Map(); attrs={}; disabled=false; hidden=false; value=""; textContent=""; dataset={};
  classes=new Set();
  classList={toggle:(name,on)=>{if(on)this.classes.add(name);else this.classes.delete(name);},contains:name=>this.classes.has(name)};
  addEventListener(name,fn){this.listeners.set(name,fn);}
  removeEventListener(name,fn){if(this.listeners.get(name)===fn)this.listeners.delete(name);}
  setAttribute(name,value){this.attrs[name]=value;}
  focus(){this.focused=true;}
  set innerHTML(html){this.html=html;}
  get innerHTML(){return this.html||"";}
}
const nodes=new Map();
const grid=new Element();
Object.defineProperty(grid,"innerHTML",{set(html){this.cards=[...html.matchAll(/data-pokemon-id="([^"]+)"/g)].map((m)=>{const card=new Element();card.dataset.pokemonId=m[1];return card;});},get(){return "";}});
grid.querySelectorAll=()=>grid.cards||[];
nodes.set("#draft-grid",grid);
const page=new Element();
Object.defineProperty(page,"innerHTML",{set(html){this.html=html;for(const[,id]of html.matchAll(/id="([^"]+)"/g))if(id!=="draft-grid")nodes.set(`#${id}`,new Element());nodes.get("#make-pick").disabled=/<button id="make-pick"[^>]*disabled/.test(html);},get(){return this.html;}});
nodes.set("#page-draft",page);
const storage=new Map(),posts=[],details=[];
let refreshes=0,failNext=false;
const context=vm.createContext({
  state:{user:{role:"COACH",teamId:8}},
  $:selector=>nodes.get(selector),
  localStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value)},
  esc:value=>String(value??""),teamBrand:team=>team.name,
  document:{querySelector:()=>null,addEventListener:()=>{}},
  api:async(url,options)=>{posts.push({url,options});if(failNext){failNext=false;throw Error("Retryable error");}},
  toast:()=>{},setInterval:()=>{},addEventListener:()=>{},removeEventListener:()=>{},
});
context.window=context;
vm.runInContext(readFileSync("src/main/resources/static/js/pokemon-ui.js","utf8"),context);
vm.runInContext(readFileSync("src/main/resources/static/js/final-ui.js","utf8"),context);
const render=context.renderDraft;
context.hydratePokemonCards=()=>{};
context.wireFinalFilters=()=>{};
context.showPokemonDetails=p=>details.push(p);
vm.runInContext(readFileSync("src/main/resources/static/js/draft-live.js","utf8"),context);
context.renderDraft=async()=>{refreshes++;};
const snapshot={settings:{draftOpen:true,draftFinalized:false,snakeDraft:true},currentTeam:{id:8,name:"Team 8"},picks:[],available:[{id:445,name:"Garchomp",price:18}]};

await render(snapshot);
assert.equal(nodes.get("#make-pick").disabled,true,"A pick requires an explicit selection");
grid.cards[0].onclick();
assert.equal(nodes.get("#draft-action-selection").textContent,"Selected: Garchomp · $18");
assert.equal(nodes.get("#make-pick").disabled,false);
assert.equal(details.at(-1).price,18);
await nodes.get("#make-pick").onclick();
assert.equal(posts.at(-1).url,"/api/draft/pick");
assert.equal(posts.at(-1).options.body.pokemonId,445);
assert.equal(refreshes,1);

await render(snapshot);grid.cards[0].onclick();failNext=true;
await nodes.get("#make-pick").onclick();
assert.equal(nodes.get("#make-pick").disabled,false,"Failed saves permit retry");
for(const [role,teamId,open,finalized]of [["GUEST",null,true,false],["COACH",9,true,false],["MANAGER",null,false,false],["MANAGER",null,true,true]]){
  context.state.user={role,teamId};
  await render({...snapshot,settings:{...snapshot.settings,draftOpen:open,draftFinalized:finalized}});
  grid.cards[0].onclick();
  assert.equal(nodes.get("#make-pick").disabled,true);
  const before=posts.length;await nodes.get("#make-pick").onclick();assert.equal(posts.length,before);
}
assert.equal(context.pokemonPriceText(null),"Price unavailable");
assert.equal(context.pokemonPriceText(0),"$0");
assert.equal(context.draftBoardPokemon({pokemonId:445,pokemonName:"Garchomp",price:18}).price,18);
assert.equal(context.draftBoardPokemon({pokemonId:1,pokemonName:"Free",price:0}).price,0);

const scroller=new Element(),position=new Element(),left=new Element(),right=new Element();
Object.assign(scroller,{scrollWidth:2600,clientWidth:1000,scrollLeft:0});left.dataset.draftScroll="-1";right.dataset.draftScroll="1";
scroller.scrollBy=({left:amount})=>{scroller.scrollLeft=Math.max(0,Math.min(scroller.scrollWidth-scroller.clientWidth,scroller.scrollLeft+amount));scroller.listeners.get("scroll")();};
let observer;
context.ResizeObserver=class {constructor(callback){this.callback=callback;observer=this;}observe(){}disconnect(){this.disconnected=true;}};
const cleanup=context.wireDraftBoardScrolling({querySelector:selector=>selector===".draft-board-scroll"?scroller:position,querySelectorAll:()=>[left,right]});
assert.equal(position.max,"1600");assert.equal(left.disabled,true);
position.value="1200";position.listeners.get("input")();assert.equal(scroller.scrollLeft,1200);
left.listeners.get("click")();assert.equal(scroller.scrollLeft,400);assert.equal(position.value,"400");
scroller.clientWidth=1800;observer.callback();assert.equal(position.max,"800","Sidebar resizing recalculates the slider");
cleanup();assert.equal(observer.disconnected,true);assert.equal(scroller.listeners.size,0);assert.equal(position.listeners.size,0);

const app=new Element(),close=new Element(),open=new Element();
const navigation=vm.createContext({document:{querySelector:s=>({"#app":app,"#sidebar-toggle":close,"#sidebar-open":open})[s]},localStorage:{getItem:key=>storage.get(key),setItem:(key,value)=>storage.set(key,value)}});
const source=readFileSync("src/main/resources/static/js/navigation-ui.js","utf8");
vm.runInContext(source,navigation);
assert.equal(open.hidden,true);
close.listeners.get("click")();assert.equal(app.classes.has("sidebar-collapsed"),true);assert.equal(open.hidden,false);assert.equal(open.focused,true);
assert.equal(close.attrs["aria-expanded"],"false");
vm.runInContext(source,navigation);assert.equal(open.hidden,false,"Collapsed preference survives reload");
open.listeners.get("click")();assert.equal(app.classes.has("sidebar-collapsed"),false);assert.equal(open.hidden,true);assert.equal(close.focused,true);
console.log("PASS: selected name/price beside button, submission/retry, coach/guest/closed/finalized guards, drafted prices including zero/unknown, scroll syncing/resize/cleanup, navigation collapse/persistence/focus");
