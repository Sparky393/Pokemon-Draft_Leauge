package com.pokemondraft.leagueportal.dto;

public record MatchupDto(Long id, int week, Long homeTeamId, Long awayTeamId, String homeTeam, String awayTeam, Integer homeScore, Integer awayScore, boolean played) {}
