package com.pokemondraft.leagueportal.dto;

import com.pokemondraft.leagueportal.entity.Team;
import java.util.List;

public record TeamRosterDto(Team team, List<RosterPokemonDto> pokemon) {
}
