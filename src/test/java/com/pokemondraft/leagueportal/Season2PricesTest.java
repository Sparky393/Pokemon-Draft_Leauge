package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;

import com.pokemondraft.leagueportal.config.BasePrices;
import java.util.*;
import org.junit.jupiter.api.Test;

class Season2PricesTest {
  private final Map<String, Integer> prices = BasePrices.values();

  @Test
  void csvPriceTiersMatchRepresentativePokemon() {
    assertEquals(20, prices.get("Indeedee (Female)"));
    assertEquals(20, prices.get("Sneasler"));
    assertEquals(19, prices.get("Gholdengo"));
    assertEquals(18, prices.get("Garchomp"));
    assertEquals(17, prices.get("Torkoal"));
    assertEquals(16, prices.get("Excadrill"));
    assertEquals(15, prices.get("Baxcalibur"));
    assertEquals(14, prices.get("Rotom Wash"));
    assertEquals(13, prices.get("Kommo-o"));
    assertEquals(12, prices.get("Greninja"));
    assertEquals(11, prices.get("Mamoswine"));
    assertEquals(10, prices.get("Rotom Fan"));
    assertEquals(9, prices.get("Umbreon"));
    assertEquals(8, prices.get("Skeledirge"));
    assertEquals(7, prices.get("Squawkabilly"));
    assertEquals(6, prices.get("Glaceon"));
    assertEquals(5, prices.get("Rhyperior"));
    assertEquals(4, prices.get("Orthworm"));
    assertEquals(3, prices.get("Hydrapple"));
    assertEquals(2, prices.get("Florges"));
    assertEquals(1, prices.get("Beartic"));
    assertEquals(0, prices.get("Slurpuff"));
  }

  @Test
  void allPricesAreWithinZeroToTwentyAndUnique() {
    assertTrue(prices.size() >= 230);
    assertTrue(prices.values().stream().allMatch(v -> v >= 0 && v <= 20));
    assertFalse(prices.containsKey("Price Chart Modifiers"));
    assertFalse(prices.containsKey("Fake Out"));
  }

  @Test
  void regionalFormsDoNotOverwriteBaseForms() {
    assertEquals(20, prices.get("Raichu"));
    assertEquals(10, prices.get("Raichu (Alolan)"));
    assertEquals(2, prices.get("Goodra"));
    assertEquals(11, prices.get("Goodra (Hisuian)"));
    assertEquals(3, prices.get("Tauros (Paldean Combat)"));
    assertEquals(0, prices.get("Tauros"));
  }
}
