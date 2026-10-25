/** Real metadata cache and filter handlers, controlled PokeAPI responses, no league server. */
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import vm from "node:vm";
const fixtures = {
  Charizard:{types:["fire","flying"],ability:"blaze",moves:["protect"],price:16},
  Garchomp:{types:["dragon","ground"],ability:"rough-skin",moves:["protect","earthquake"],price:18},
  Raichu:{types:["electric"],ability:"static",moves:["protect"],price:8},
  "Rotom Wash":{types:["electric","water"],ability:"levitate",moves:["protect","hydro-pump"],price:12},
  Kingambit:{types:["dark","steel"],ability:"defiant",moves:["protect"],price:18},
};
const source=Object.entries(fixtures).map(([name,data],i)=>({id:i+1,name,price:data.price}));
const fields=new Map(),calls=[];
const storage=new Map([["draftLeaguePokemonMetaV6",JSON.stringify({Charizard:{image:"old.png",abilities:["Blaze"],moves:["Protect"],stats:[{name:"Hp",value:78}]}})]]);
const context=vm.createContext({
  localStorage:{getItem:key=>storage.get(key),setItem:(key,value)=>storage.set(key,value)},
  $:selector=>fields.get(selector),esc:value=>String(value??""),
  fetch:async(url)=>{
    assert.match(url,/^https:\/\/pokeapi\.co\/api\/v2\/pokemon\//,"Type filtering must never contact the league database");
    calls.push(url);
    const slug=url.split("/").at(-1),name=Object.keys(fixtures).find(name=>name.toLowerCase().replaceAll(" ","-")===slug);
    assert.ok(name,`Missing test metadata for ${slug}`);
    const f=fixtures[name];
    return {ok:true,json:async()=>({sprites:{front_default:`${name}.png`},types:f.types.map((name,i)=>({slot:i+1,type:{name}})),abilities:[{ability:{name:f.ability}}],moves:f.moves.map(name=>({move:{name}})),stats:[{stat:{name:"hp"},base_stat:80}]})};
  },
});
for(const file of ["pokemon-ui.js","final-ui.js"])vm.runInContext(readFileSync(`src/main/resources/static/js/${file}`,"utf8"),context);
const plain=value=>JSON.parse(JSON.stringify(value));
const metadata=await Promise.all([context.getPokemonMeta("Charizard"),context.getPokemonMeta("Charizard"),context.getPokemonMeta("Charizard")]);
assert.equal(calls.length,1,"Concurrent cache upgrades share one metadata request");
assert.deepEqual(plain(metadata[0].types),["Fire","Flying"]);
assert.deepEqual(JSON.parse(storage.get("draftLeaguePokemonMetaV6")).Charizard.types,["Fire","Flying"]);
await context.getPokemonMeta("Charizard");assert.equal(calls.length,1,"Updated types are cached");
const options=[...context.pokemonTypeFilter("draft").matchAll(/<option value="([^"]+)">([^<]+)<\/option>/g)];
assert.equal(options.length,19);
assert.equal(new Set(options.map(m=>m[1])).size,19);
assert.deepEqual(options.slice(1).map(m=>m[2]),["Bug","Dark","Dragon","Electric","Fairy","Fighting","Fire","Flying","Ghost","Grass","Ground","Ice","Normal","Poison","Psychic","Rock","Steel","Water"]);

function inputs(prefix,advanced=false){
  for(const name of ["search","move","ability","sort","mega","type","clear","price","move-list","ability-list"]){
    fields.set(`#${prefix}-${name}`,{value:["mega","type"].includes(name)?"all":["sort","price"].includes(name)?"name":"",listeners:{},addEventListener(event,fn){this.listeners[event]=fn;}});
  }
}
for(const prefix of ["draft","fa"]){
  inputs(prefix);
  let rows=[];
  await context.wireFinalFilters(prefix,source,result=>{rows=plain(result);});
  await Promise.all(source.map(p=>context.getPokemonMeta(p.name)));
  const field=name=>fields.get(`#${prefix}-${name}`);
  const change=async(name,value,event="change")=>{field(name).value=value;await field(name).listeners[event]();};
  await change("type","fire");assert.deepEqual(rows.map(p=>p.name),["Charizard"]);
  await change("type","flying");assert.deepEqual(rows.map(p=>p.name),["Charizard"],"Either side of a dual type matches");
  await change("type","electric");assert.deepEqual(rows.map(p=>p.name),["Raichu","Rotom Wash"]);
  await change("sort","price-high");assert.deepEqual(rows.map(p=>p.name),["Rotom Wash","Raichu"]);
  await change("ability","static","input");assert.deepEqual(rows.map(p=>p.name),["Raichu"]);
  await change("ability","","input");
  await change("type","ground");await change("move","earthquake","input");assert.deepEqual(rows.map(p=>p.name),["Garchomp"]);
  await change("move","","input");await change("search","garch","input");assert.deepEqual(rows.map(p=>p.name),["Garchomp"]);
  await change("type","fairy");assert.deepEqual(rows,[]);
  await field("clear").onclick();assert.equal(field("type").value,"all");assert.equal(rows.length,source.length);
  await change("mega","mega");await change("type","fire");assert.deepEqual(rows.map(p=>p.name),["Charizard"]);
  await field("clear").onclick();

  // An older slow metadata result cannot overwrite a newer type selection.
  const original=context.getPokemonFullMeta;
  let release,first=true;
  context.getPokemonFullMeta=name=>first?(first=false,new Promise(resolve=>{release=resolve;})):original(name);
  field("type").value="ground";const older=field("type").listeners.change();
  await change("type","fire");assert.deepEqual(rows.map(p=>p.name),["Charizard"]);
  release(await original("Charizard"));await older;
  assert.deepEqual(rows.map(p=>p.name),["Charizard"],"Only the latest selection may redraw");
  context.getPokemonFullMeta=original;
}

inputs("legacy");let legacy=[];
await context.wireAdvancedFilters("legacy",source,rows=>{legacy=plain(rows);});
fields.get("#legacy-type").value="water";await fields.get("#legacy-type").listeners.change();
assert.deepEqual(legacy.map(p=>p.name),["Rotom Wash"]);
await fields.get("#legacy-clear").onclick();assert.equal(legacy.length,source.length);
assert.equal(calls.length,source.length,"Metadata warming and repeated filtering reuse the same per-Pokémon cache");
console.log("PASS: 18 types, both dual-type slots, draft/free-agent/legacy filters, combined search/move/ability/sort/Mega filters, reset, stale-result guard, legacy cache upgrade, shared requests; no league API requests");
