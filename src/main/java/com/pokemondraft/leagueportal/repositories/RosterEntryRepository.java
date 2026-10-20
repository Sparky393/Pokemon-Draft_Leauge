package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.RosterEntry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, Long> {
  List<RosterEntry> findByTeamId(Long teamId);

  Optional<RosterEntry> findByPokemonId(Long pokemonId);

  long countByTeamId(Long teamId);
}
