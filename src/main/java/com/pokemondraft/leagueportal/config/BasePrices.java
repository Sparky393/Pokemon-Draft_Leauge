package com.pokemondraft.leagueportal.config;

import java.util.*;

/**
 * Season 2 pricing, transcribed from the commissioner's Draft Price Chart CSV. Ability/move
 * modifiers are not Pokemon and are deliberately excluded. Regional and alternate-form labels are
 * normalized to existing portal names.
 */
public final class BasePrices {
  private BasePrices() {}

  public static Map<String, Integer> values() {
    Map<String, Integer> out = new LinkedHashMap<>();
    String data =
"""
20:Indeedee (Female)|Indeedee (Male)|Whimsicott|Pelipper|Rillaboom|Sneasler|Incineroar|Salamence|Golisopod|Raichu|Farigiraf|Volcarona|Grimmsnarl|Kingambit|Milotic|Gardevoir|Armarouge|Staraptor|Sinistcha|Talonflame|Vivillon
19:Basculegion (Male)|Gholdengo|Sylveon|Gengar|Sableye|Corviknight|Pawmot|Blastoise|Hatterene
18:Garchomp|Archaludon|Charizard|Arcanine (Hisuian)|Tyranitar|Metagross|Floette (Eternal Flower)|Lucario|Swampert|Dragonite|Aerodactyl|Kleavor
17:Torkoal|Politoed|Froslass|Primarina|Glimmora|Delphox|Annihilape|Maushold|Hydreigon|Gallade|Meowstic (Female)
16:Meowstic (Male)|Excadrill|Ninetales (Alolan)|Absol|Dragapult|Mawile|Meowscarada|Espathra|Malamar|Scizor
15:Baxcalibur|Venusaur|Ceruledge|Camerupt|Blaziken|Tsareena|Empoleon|Weavile|Chandelure|Clefable
14:Sirfetch'd|Rotom Wash|Alakazam|Scovillain|Mimikyu|Toxtricity (Amped)|Scrafty|Altaria|Klefki
13:Kommo-o|Typhlosion (Hisuian)|Inteleon|Zoroark (Hisuian)|Aegislash|Pincurchin|Toxapex|Cinderace|Kangaskhan|Meganium|Basculegion (Female)|Samurott (Hisuian)|Lopunny|Starmie
12:Grapploct|Greninja|Oranguru|Gyarados|Overqwil|Rotom Heat|Araquanid|Toxtricity (Low Key)|Lycanroc (Dusk)|Sceptile|Ninetales|Slowking (Galarian)|Medicham
11:Goodra (Hisuian)|Mamoswine|Snorlax|Bellibolt|Pyroar|Tinkaton|Vanilluxe|Arcanine|Clawitzer|Skarmory|Gliscor|Infernape|Mr. Mime
10:Drampa|Jolteon|Spiritomb|Hawlucha|Audino|Eelektross|Steelix|Slowbro|Crabominable|Raichu (Alolan)|Slowbro (Galarian)|Espeon|Perrserker|Rotom Frost|Rotom Mow|Rotom Fan
9:Aggron|Umbreon|Azumarill|Ampharos|Abomasnow|Heliolisk|Noivern|Palafin|Dragalge|Feraligatr|Wyrdeer|Liepard
8:Skeledirge|Serperior|Alcremie|Golurk|Krookodile|Conkeldurr|Manectric|Cofagrigus|Decidueye (Hisuian)|Persian (Alolan)|Mr. Rime|Banette
7:Ditto|Houndoom|Houndstone|Chesnaught|Hippowdon|Salazzle|Arboliva|Wigglytuff|Reuniclus|Pidgeot|Toxicroak|Squawkabilly
6:Glaceon|Mudsdale|Vileplume|Vaporeon|Aurorus|Scolipede|Runerigus|Pikachu|Beedrill|Aromatisse|Slowking
5:Rhyperior|Torterra|Heracross|Typhlosion|Machamp|Tauros (Paldean Blaze)|Glalie|Quaquaval|Tauros (Paldean Aqua)|Morpeko|Garganacl|Sharpedo|Pangoro|Chimecho|Toucannon
4:Orthworm|Tyrantrum|Trevenant|Farfetch'd
3:Hydrapple|Zoroark|Gogoat|Mabosstiff|Rampardos|Thievul|Emboar|Leafeon|Avalugg (Hisuian)|Luxray|Diggersby|Polteageist|Passimian|Tauros (Paldean Combat)
2:Lycanroc (Midday)|Victreebel|Florges|Goodra|Musharna|Falinks|Bastiodon|Decidueye|Flapple|Persian
1:Beartic|Gourgeist|Pinsir|Emolga|Flareon|Arbok|Barbaracle|Castform|Ariados|Qwilfish
0:Slurpuff|Swalot|Roserade|Dedenne|Sandaconda|Stunfisk|Forretress|Appletun|Lycanroc (Midnight)|Furfrou|Avalugg|Garbodor|Simipour|Tauros|Stunfisk (Galarian)|Rotom|Simisage|Simisear|Watchog|Samurott
""";
    for (String line : data.strip().split("\n")) {
      String[] pair = line.split(":", 2);
      int price = Integer.parseInt(pair[0]);
      for (String name : pair[1].split("\\|")) {
        if (out.putIfAbsent(name, price) != null)
          throw new IllegalStateException("Duplicate price: " + name);
      }
    }
    return Collections.unmodifiableMap(out);
  }
}
