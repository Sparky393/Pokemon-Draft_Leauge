package com.pokemondraft.leagueportal.dto;

public record DraftPickDto(
    int overallPick, int round, Long teamId, String teamName, Long pokemonId, String pokemonName, Integer price) {}
