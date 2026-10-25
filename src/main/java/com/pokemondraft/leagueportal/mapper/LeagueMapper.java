package com.pokemondraft.leagueportal.mapper;

import com.pokemondraft.leagueportal.dto.*;
import com.pokemondraft.leagueportal.entity.*;
import java.util.Map;

/** Converts database entities into the existing public API response shapes. */
public final class LeagueMapper {
  private LeagueMapper() {}

  public static RosterPokemonDto rosterPokemon(RosterEntry entry, Pokemon pokemon) {
    return new RosterPokemonDto(
        entry.getId(),
        entry.getPokemonId(),
        pokemon == null ? "Unknown" : pokemon.getName(),
        pokemon == null ? 0 : pokemon.getPrice());
  }

  public static LineupPokemonDto lineupPokemon(Pokemon pokemon) {
    return new LineupPokemonDto(pokemon.getId(), pokemon.getName(), pokemon.getPrice());
  }

  public static DraftPickDto draftPick(
      DraftPick pick, Map<Long, String> teamNames, Map<Long, String> pokemonNames, Map<Long, Integer> prices) {
    return new DraftPickDto(
        pick.getOverallPick(),
        pick.getRoundNumber(),
        pick.getTeamId(),
        teamNames.get(pick.getTeamId()),
        pick.getPokemonId(),
        pokemonNames.getOrDefault(pick.getPokemonId(), "Unknown"),
        prices.get(pick.getPokemonId()));
  }

  public static MatchupDto matchup(Matchup matchup, Map<Long, String> teamNames) {
    return new MatchupDto(
        matchup.getId(),
        matchup.getWeek(),
        matchup.getHomeTeamId(),
        matchup.getAwayTeamId(),
        teamNames.get(matchup.getHomeTeamId()),
        teamNames.get(matchup.getAwayTeamId()),
        matchup.getHomeScore(),
        matchup.getAwayScore(),
        matchup.isPlayed());
  }

  public static UserDto user(AppUser user) {
    return new UserDto(user.getId(), user.getUsername(), user.getRole().name(), user.getTeamId());
  }

  public static UserDto authUser(AppUser user) {
    return new UserDto(
        user.getId(),
        user.getUsername(),
        user.getRole().name(),
        user.getTeamId() == null ? "" : user.getTeamId());
  }
}
