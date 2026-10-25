/** Shared, browser-only fixture adapter. It never forwards requests to a league server. */
export function installDraftPreview() {
  const sample = [
    ["Garchomp",445,18],["Charizard",6,16],["Raichu",26,8],["Blastoise",9,12],
    ["Venusaur",3,12],["Dragonite",149,18],["Salamence",373,17],["Metagross",376,17],
    ["Lucario",448,14],["Gengar",94,15],["Alakazam",65,12],["Scizor",212,13],
    ["Tyranitar",248,18],["Gyarados",130,14],["Arcanine",59,10],["Ninetales",38,8],
    ["Milotic",350,10],["Togekiss",468,13],["Excadrill",530,14],["Volcarona",637,17],
    ["Rotom Wash",10009,12],["Greninja",658,16],["Mimikyu",778,12],["Dragapult",887,18],
    ["Meowscarada",908,14],["Corviknight",823,12],["Kingambit",983,18],["Incineroar",727,16],
    ["Primarina",730,13],["Basculegion (Female)",10248,14],["Goodra (Hisuian)",10242,13],
    ["Palafin",964,14],["Raichu (Alolan)",10100,9],["Absol",359,8],["Meowstic (Male)",678,6],
    ["Toxtricity (Low Key)",10184,10],["Skeledirge",911,11],["Maushold",925,8],
  ];
  const typeNames = {
    Garchomp:["Dragon","Ground"], Charizard:["Fire","Flying"], Raichu:["Electric"], Blastoise:["Water"],
    Venusaur:["Grass","Poison"], Dragonite:["Dragon","Flying"], Salamence:["Dragon","Flying"], Metagross:["Steel","Psychic"],
    Lucario:["Fighting","Steel"], Gengar:["Ghost","Poison"], Alakazam:["Psychic"], Scizor:["Bug","Steel"],
    Tyranitar:["Rock","Dark"], Gyarados:["Water","Flying"], Arcanine:["Fire"], Ninetales:["Fire"],
    Milotic:["Water"], Togekiss:["Fairy","Flying"], Excadrill:["Ground","Steel"], Volcarona:["Bug","Fire"],
    "Rotom Wash":["Electric","Water"], Greninja:["Water","Dark"], Mimikyu:["Ghost","Fairy"], Dragapult:["Dragon","Ghost"],
    Meowscarada:["Grass","Dark"], Corviknight:["Flying","Steel"], Kingambit:["Dark","Steel"], Incineroar:["Fire","Dark"],
    Primarina:["Water","Fairy"], "Basculegion (Female)":["Water","Ghost"], "Goodra (Hisuian)":["Steel","Dragon"],
    Palafin:["Water"], "Raichu (Alolan)":["Electric","Psychic"], Absol:["Dark"], "Meowstic (Male)":["Psychic"],
    "Toxtricity (Low Key)":["Electric","Poison"], Skeledirge:["Fire","Ghost"], Maushold:["Normal"],
  };
  const pokemon = sample.map(([name,dex,price],i) => ({ id:i+1,name,dex,price,drafted:i<24 }));
  const teams = Array.from({length:16},(_,i) => ({id:i+1,name:["Thunderbolts","Dragon Tamers","Kanto Kings","Moonlight","Iron Wings","Ocean Guard","Flame Riders","Night Owls","Johto Giants","Mossy Rocks","Storm Watch","Crystal Crew","Silver Claws","Nova Squad","Aurora","Final Frontier"][i],coachName:`Coach ${i+1}`,division:i<8?"A":"B",draftPosition:i+1,wins:0,losses:0,differential:0,faBudget:3}));
  const settings = {leagueName:"Draft Layout Preview",currentWeek:1,regularSeasonWeeks:13,draftOpen:true,draftFinalized:false,snakeDraft:true,lineupDeadline:null,waiverWindowOpen:false,waiverClosedReason:"Mock preview: free agency is closed.",waiverPeriodsProcessed:0};
  const teamForPick = count => {const slot=count%16;return teams[Math.floor(count/16)%2?15-slot:slot];};
  const picks = pokemon.slice(0,24).map((p,i)=>({id:i+1,overallPick:i+1,round:Math.floor(i/16)+1,teamId:teamForPick(i).id,teamName:teamForPick(i).name,pokemonId:p.id,pokemonName:p.name,price:p.price}));
  let user={id:8,username:"Preview Coach",role:"COACH",teamId:8};
  const roster = teamId => picks.filter(p=>p.teamId===Number(teamId)).map(p=>({rosterId:p.id,pokemonId:p.pokemonId,name:p.pokemonName,price:p.price}));
  const revision = () => `preview:${picks.length}:${settings.draftOpen}`;
  const draft = () => ({currentTeam:teamForPick(picks.length),picks:[...picks],available:pokemon.filter(p=>!p.drafted),settings:{...settings},lastPickId:picks.at(-1)?.id,idleRevision:revision()});
  const json = (value,status=200) => new Response(JSON.stringify(value),{status,headers:{"Content-Type":"application/json"}});
  window.fetch = async (input,options={}) => {
    const url=new URL(String(input),"https://draft-preview.invalid");
    if(url.hostname==="pokeapi.co") {
      const slug=decodeURIComponent(url.pathname.split("/").filter(Boolean).at(-1));
      const p=pokemon.find(p=>typeof pokeSlug==="function"&&pokeSlug(p.name)===slug)||pokemon.find(p=>p.name.toLowerCase().split(" (")[0].replaceAll(" ","-")===slug)||pokemon[0];
      return json({types:(typeNames[p.name]||[]).map((name,i)=>({slot:i+1,type:{name:name.toLowerCase()}})),sprites:{other:{"official-artwork":{front_default:`https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/${p.dex}.png`}}},abilities:[{ability:{name:p.name==="Garchomp"?"rough-skin":"sample-ability"}}],moves:[{move:{name:"protect"}}],stats:["hp","attack","defense","special-attack","special-defense","speed"].map((name,i)=>({stat:{name},base_stat:[80,100,85,90,85,100][i]}))});
    }
    if(!url.pathname.startsWith("/api/")) return json({message:"This preview blocks server requests."},404);
    const path=url.pathname,method=options.method||"GET";
    const body=typeof options.body==="string"?JSON.parse(options.body):options.body||{};
    if(path==="/api/auth/me")return user?json(user):json({},401);
    if(path==="/api/auth/guest"){user={id:null,username:"Guest",role:"GUEST",teamId:null};return json(user);}
    if(path==="/api/auth/login") {user={id:8,username:body.username||"Preview Coach",role:body.username==="manager"?"MANAGER":"COACH",teamId:body.username==="manager"?null:8};return json(user);}
    if(path==="/api/auth/logout"){user=null;return json(null);}
    if(!user)return json({},401);
    if(path==="/api/draft/pick"&&method==="POST") {
      if(user.role!=="MANAGER"&&!(user.role==="COACH"&&user.teamId===teamForPick(picks.length).id))return json({message:"It is another team's turn."},403);
      if(!settings.draftOpen)return json({message:"The preview draft is closed."},400);
      const p=pokemon.find(p=>p.id===Number(body.pokemonId)&&!p.drafted);
      if(!p)return json({message:"Choose an available Pokémon."},400);
      const team=teamForPick(picks.length);
      if(roster(team.id).reduce((sum,p)=>sum+p.price,0)+p.price>100)return json({message:"This pick exceeds the $100 budget."},400);
      p.drafted=true;const next=picks.length+1;
      picks.push({id:next,overallPick:next,round:Math.floor((next-1)/16)+1,teamId:team.id,teamName:team.name,pokemonId:p.id,pokemonName:p.name,price:p.price});
      return json(picks.at(-1));
    }
    if(path==="/api/manager/draft/undo"&&method==="POST"&&user.role==="MANAGER") {const p=picks.pop();if(p)pokemon.find(x=>x.id===p.pokemonId).drafted=false;return json(null);}
    if(method!=="GET")return json({message:"This is a local mock preview. Only simulated draft picks are enabled."},403);
    if(path==="/api/dashboard/view"||path==="/api/dashboard")return json({user,teams,settings,currentDraftTeam:teamForPick(picks.length)});
    if(path==="/api/teams")return json(teams);
    if(path==="/api/settings")return json(settings);
    if(path==="/api/draft")return json(draft());
    if(path==="/api/draft/status")return json({pickCount:picks.length,lastPickId:picks.at(-1)?.id,currentTeamId:teamForPick(picks.length).id,draftOpen:settings.draftOpen,draftFinalized:false,snakeDraft:true});
    if(path==="/api/draft/idle-status")return json({revision:revision()});
    if(path==="/api/pokemon")return json(pokemon);
    if(path==="/api/pokemon/free-agents")return json(pokemon.filter(p=>!p.drafted));
    if(path==="/api/rosters")return json(teams.map(team=>({team,pokemon:roster(team.id)})));
    if(path.startsWith("/api/rosters/"))return json(roster(path.split("/").at(-1)));
    if(path==="/api/standings")return json({A:teams.slice(0,8),B:teams.slice(8),seedModeEnabled:false});
    if(path==="/api/playoffs")return json({games:[],regularSeasonComplete:false,regularGamesRemaining:80});
    if(path==="/api/flow/calendar")return json([{week:1,label:"Preview week",lineupRequired:true}]);
    if(path==="/api/flow/lineup")return json({week:1,weekLabel:"Preview week",lineupRequired:true,locked:false,deadline:null,roster:roster(user.teamId),selectedPokemonIds:[]});
    if(path==="/api/weeks/locks")return json({});
    if(path==="/api/auth/security")return json({usingInitialPassword:false});
    if(["/api/flow/schedule","/api/schedule","/api/transactions","/api/free-agency/bids","/api/notifications","/api/manager/reminders","/api/auth/manager/users"].includes(path))return json([]);
    return json({message:"That page isn't part of this mock preview."},404);
  };
  window.setDraftPreviewRole = async role => {
    user={id:role==="GUEST"?null:8,username:`Preview ${role}`,role,teamId:role==="COACH"?8:null};
    await enterApp();await showPage("draft");
  };
  window.toggleDraftPreviewOpen = async () => {settings.draftOpen=!settings.draftOpen;await renderDraft();};
}
