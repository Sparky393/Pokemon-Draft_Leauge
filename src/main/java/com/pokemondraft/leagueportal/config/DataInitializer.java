package com.pokemondraft.leagueportal.config;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.ScheduleService;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {
  @Bean
  CommandLineRunner seed(
      AppUserRepository users,
      TeamRepository teams,
      PokemonRepository pokemon,
      LeagueSettingsRepository settings,
      MatchupRepository matchups,
      RosterEntryRepository roster,
      ScheduleService schedule) {
    return args -> {
      if (settings.count() == 0) settings.save(new LeagueSettings());
      if (teams.count() == 0) {
        List<Team> saved = new ArrayList<>();
        for (int i = 1; i <= 16; i++) {
          String div = i <= 8 ? "A" : "B";
          saved.add(teams.save(new Team("Team " + i, "Coach " + i, div, i)));
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        users.save(new AppUser("manager", encoder.encode("manager"), Role.MANAGER, null));
        for (int i = 1; i <= 16; i++)
          users.save(
              new AppUser(
                  "coach" + i, encoder.encode("password"), Role.COACH, saved.get(i - 1).getId()));
      }
      // Existing user names, passwords, divisions, budgets, roster ownership,
      // draft order and team identities belong to the live database.
      // Do not reconcile, reprice, or reset them on code deployments.
      Map<String, Integer> basePrices = BasePrices.values();
      List<Pokemon> pool = new ArrayList<>(pokemon.findAll());
      if (pool.isEmpty()) {
        for (String name : championsPool())
          pool.add(pokemon.save(new Pokemon(name, basePrices.getOrDefault(name, 0))));
      } else {
        // Existing league data is authoritative. On restart/deploy, only add
        // newly supported Pokemon; never overwrite commissioner prices, roster
        // state, or delete existing Pokemon from a live database.
        Set<String> existingNames = new HashSet<>();
        for (Pokemon p : pool) existingNames.add(p.getName());
        for (String name : championsPool())
          if (!existingNames.contains(name))
            pool.add(pokemon.save(new Pokemon(name, basePrices.getOrDefault(name, 0))));
      }
      // League prices and current week are authoritative after initial setup.
      // Future features must use additive schema changes, never silently reset
      // existing data when Render restarts.
      // RosterEntry is the source of truth for current ownership. Repair only
      // the derived Pokemon.drafted flag; never rebuild or remove roster rows.
      Set<Long> rosteredPokemonIds = new HashSet<>();
      for (RosterEntry entry : roster.findAll()) rosteredPokemonIds.add(entry.getPokemonId());
      for (Pokemon p : pool) {
        boolean shouldBeDrafted = rosteredPokemonIds.contains(p.getId());
        if (p.isDrafted() != shouldBeDrafted) {
          p.setDrafted(shouldBeDrafted);
          pokemon.save(p);
        }
      }

      // Idempotent official fixture migration; keeps matching recorded results.
      if (teams.count() == 16 && matchups.count() == 0) schedule.syncOfficialSchedule();
    };
  }

  private List<String> championsPool() {
    return List.of(
        "Venusaur",
        "Charizard",
        "Blastoise",
        "Beedrill",
        "Pidgeot",
        "Arbok",
        "Pikachu",
        "Raichu",
        "Raichu (Alolan)",
        "Clefable",
        "Ninetales",
        "Ninetales (Alolan)",
        "Wigglytuff",
        "Vileplume",
        "Persian",
        "Persian (Alolan)",
        "Arcanine",
        "Arcanine (Hisuian)",
        "Alakazam",
        "Machamp",
        "Victreebel",
        "Slowbro",
        "Slowbro (Galarian)",
        "Farfetch'd",
        "Gengar",
        "Kangaskhan",
        "Starmie",
        "Mr. Mime",
        "Pinsir",
        "Tauros",
        "Tauros (Paldean Combat)",
        "Tauros (Paldean Blaze)",
        "Tauros (Paldean Aqua)",
        "Gyarados",
        "Ditto",
        "Vaporeon",
        "Jolteon",
        "Flareon",
        "Aerodactyl",
        "Snorlax",
        "Dragonite",
        "Meganium",
        "Typhlosion",
        "Typhlosion (Hisuian)",
        "Feraligatr",
        "Ariados",
        "Ampharos",
        "Azumarill",
        "Politoed",
        "Espeon",
        "Umbreon",
        "Slowking",
        "Slowking (Galarian)",
        "Forretress",
        "Steelix",
        "Qwilfish",
        "Scizor",
        "Heracross",
        "Skarmory",
        "Houndoom",
        "Tyranitar",
        "Sceptile",
        "Blaziken",
        "Swampert",
        "Pelipper",
        "Gardevoir",
        "Sableye",
        "Mawile",
        "Aggron",
        "Medicham",
        "Manectric",
        "Swalot",
        "Sharpedo",
        "Camerupt",
        "Torkoal",
        "Altaria",
        "Milotic",
        "Castform",
        "Banette",
        "Chimecho",
        "Absol",
        "Glalie",
        "Salamence",
        "Metagross",
        "Torterra",
        "Infernape",
        "Empoleon",
        "Staraptor",
        "Luxray",
        "Roserade",
        "Rampardos",
        "Bastiodon",
        "Lopunny",
        "Spiritomb",
        "Garchomp",
        "Lucario",
        "Hippowdon",
        "Toxicroak",
        "Abomasnow",
        "Weavile",
        "Rhyperior",
        "Leafeon",
        "Glaceon",
        "Gliscor",
        "Mamoswine",
        "Gallade",
        "Froslass",
        "Rotom",
        "Rotom Heat",
        "Rotom Wash",
        "Rotom Frost",
        "Rotom Fan",
        "Rotom Mow",
        "Serperior",
        "Emboar",
        "Samurott",
        "Samurott (Hisuian)",
        "Watchog",
        "Liepard",
        "Simisage",
        "Simisear",
        "Simipour",
        "Musharna",
        "Excadrill",
        "Audino",
        "Conkeldurr",
        "Scolipede",
        "Whimsicott",
        "Krookodile",
        "Scrafty",
        "Cofagrigus",
        "Garbodor",
        "Zoroark",
        "Zoroark (Hisuian)",
        "Reuniclus",
        "Vanilluxe",
        "Emolga",
        "Eelektross",
        "Chandelure",
        "Beartic",
        "Stunfisk",
        "Stunfisk (Galarian)",
        "Golurk",
        "Hydreigon",
        "Volcarona",
        "Chesnaught",
        "Delphox",
        "Greninja",
        "Diggersby",
        "Talonflame",
        "Vivillon",
        "Pyroar",
        "Floette (Eternal Flower)",
        "Florges",
        "Gogoat",
        "Pangoro",
        "Furfrou",
        "Meowstic (Male)",
        "Meowstic (Female)",
        "Aegislash",
        "Aromatisse",
        "Slurpuff",
        "Malamar",
        "Barbaracle",
        "Dragalge",
        "Clawitzer",
        "Heliolisk",
        "Tyrantrum",
        "Aurorus",
        "Sylveon",
        "Hawlucha",
        "Dedenne",
        "Goodra",
        "Goodra (Hisuian)",
        "Klefki",
        "Trevenant",
        "Gourgeist",
        "Avalugg",
        "Avalugg (Hisuian)",
        "Noivern",
        "Decidueye",
        "Decidueye (Hisuian)",
        "Incineroar",
        "Primarina",
        "Toucannon",
        "Crabominable",
        "Lycanroc (Midday)",
        "Lycanroc (Midnight)",
        "Lycanroc (Dusk)",
        "Toxapex",
        "Mudsdale",
        "Araquanid",
        "Salazzle",
        "Tsareena",
        "Oranguru",
        "Passimian",
        "Golisopod",
        "Mimikyu",
        "Drampa",
        "Kommo-o",
        "Rillaboom",
        "Cinderace",
        "Inteleon",
        "Corviknight",
        "Thievul",
        "Flapple",
        "Appletun",
        "Sandaconda",
        "Toxtricity (Amped)",
        "Toxtricity (Low Key)",
        "Grapploct",
        "Polteageist",
        "Hatterene",
        "Grimmsnarl",
        "Perrserker",
        "Sirfetch'd",
        "Mr. Rime",
        "Runerigus",
        "Alcremie",
        "Falinks",
        "Pincurchin",
        "Indeedee (Male)",
        "Indeedee (Female)",
        "Morpeko",
        "Dragapult",
        "Wyrdeer",
        "Kleavor",
        "Basculegion (Male)",
        "Basculegion (Female)",
        "Sneasler",
        "Overqwil",
        "Meowscarada",
        "Skeledirge",
        "Quaquaval",
        "Pawmot",
        "Maushold",
        "Arboliva",
        "Squawkabilly",
        "Garganacl",
        "Armarouge",
        "Ceruledge",
        "Bellibolt",
        "Mabosstiff",
        "Scovillain",
        "Espathra",
        "Tinkaton",
        "Palafin",
        "Orthworm",
        "Glimmora",
        "Houndstone",
        "Annihilape",
        "Farigiraf",
        "Kingambit",
        "Baxcalibur",
        "Gholdengo",
        "Sinistcha",
        "Archaludon",
        "Hydrapple");
  }
}
