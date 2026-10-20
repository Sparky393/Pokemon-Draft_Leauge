package com.pokemondraft.leagueportal.config;

import org.springframework.stereotype.Component;

/**
 * Historical compatibility placeholder. Live Pokémon and draft rows are never rewritten or removed
 * on deployment, even if a future version of the Pokémon pool is different. New items are seeded
 * only in DataInitializer.
 */
@Component
public class PokemonPoolMigration {
  // Intentionally no automatic migration for this live, one-season league.
}
