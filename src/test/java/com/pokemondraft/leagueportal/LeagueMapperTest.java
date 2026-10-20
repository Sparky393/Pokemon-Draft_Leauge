package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;

import com.pokemondraft.leagueportal.dto.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.mapper.LeagueMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LeagueMapperTest {
  @Test
  void mapsRosterWithoutAddingEntityFields() {
    Pokemon pokemon = new Pokemon("Charizard", 12);
    RosterEntry entry = new RosterEntry(1L, 6L);
    RosterPokemonDto roster = LeagueMapper.rosterPokemon(entry, pokemon);
    assertEquals(Long.valueOf(6L), roster.pokemonId());
    assertEquals("Charizard", roster.name());
    assertEquals(12, roster.price());
    LineupPokemonDto lineup = LeagueMapper.lineupPokemon(pokemon);
    assertEquals("Charizard", lineup.name());
    assertEquals(12, lineup.price());
  }

  @Test
  void preservesMissingPokemonFallback() {
    RosterPokemonDto dto = LeagueMapper.rosterPokemon(new RosterEntry(1L, 6L), null);
    assertEquals("Unknown", dto.name());
    assertEquals(0, dto.price());
  }

  @Test
  void preservesAuthTeamIdConvention() {
    AppUser manager = new AppUser("manager", "password", Role.MANAGER, null);
    assertNull(LeagueMapper.user(manager).teamId());
    assertEquals("", LeagueMapper.authUser(manager).teamId());
  }

  @Test
  void mapsScheduleNamesAndNullableScores() {
    Matchup matchup = new Matchup(1, 1L, 2L);
    MatchupDto dto = LeagueMapper.matchup(matchup, Map.of(1L, "Team 1", 2L, "Team 2"));
    assertEquals("Team 1", dto.homeTeam());
    assertEquals("Team 2", dto.awayTeam());
    assertNull(dto.homeScore());
    assertFalse(dto.played());
  }
}
