package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.RosterEntry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, Long> {
  List<RosterEntry> findByTeamId(Long teamId);

  List<RosterEntry> findByTeamIdIn(Collection<Long> teamIds);

  Optional<RosterEntry> findByPokemonId(Long pokemonId);

  long countByTeamId(Long teamId);

  @org.springframework.data.jpa.repository.Query(
      "select coalesce(sum(p.price), 0) from RosterEntry r, Pokemon p where r.pokemonId = p.id and"
          + " r.teamId = :teamId")
  long totalPriceByTeamId(Long teamId);
}
