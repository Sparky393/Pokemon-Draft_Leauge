package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.ScheduleService;
import com.pokemondraft.leagueportal.services.WeekLockService;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScheduleServiceTest {
  @Test
  void recordingAndClearingResultsRecalculatesStandings() {
    TeamRepository teams = mock(TeamRepository.class);
    MatchupRepository matchups = mock(MatchupRepository.class);
    LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
    ScheduleService schedule = new ScheduleService(teams, matchups, settings,
        mock(WeekLockService.class));
    Team home = new Team("Home", "Coach", "A", 1);
    Team away = new Team("Away", "Coach", "B", 2);
    org.springframework.test.util.ReflectionTestUtils.setField(home, "id", 1L);
    org.springframework.test.util.ReflectionTestUtils.setField(away, "id", 2L);
    Matchup match = new Matchup(1, 1L, 2L);
    when(matchups.findById(5L)).thenReturn(Optional.of(match));
    when(matchups.findAll()).thenReturn(List.of(match));
    when(teams.findAllMetadata()).thenReturn(List.of(home, away));
    schedule.setResult(5L, 2, 1);
    assertTrue(match.isPlayed());
    assertEquals(1, home.getWins());
    assertEquals(1, away.getLosses());
    assertEquals(1, home.getDifferential());
    schedule.clearResult(5L);
    assertFalse(match.isPlayed());
    assertEquals(0, home.getWins());
    assertEquals(0, away.getLosses());
    assertEquals(0, home.getDifferential());
  }

  @Test
  void everyRegularSeasonGameRequiresExactlyThreeGames() {
    for (int week : new int[] { 1, 2, 3, 5, 6, 7, 10, 11, 12, 13 }) {
      for (int[] score : new int[][] { { 3, 0 }, { 2, 1 }, { 1, 2 }, { 0, 3 } })
        assertTrue(ScheduleService.validSeriesScore(week, score[0], score[1]));
      for (int[] score : new int[][] { { 2, 0 }, { 0, 2 }, { 3, 1 }, { 3, 2 }, { 2, 2 },
          { -1, 4 } })
        assertFalse(ScheduleService.validSeriesScore(week, score[0], score[1]));
    }
  }

  @Test
  void everyPlayoffRoundStopsAtTwoWins() {
    for (int week : new int[] { 14, 15, 16 }) {
      for (int[] score : new int[][] { { 2, 0 }, { 2, 1 }, { 1, 2 }, { 0, 2 } })
        assertTrue(ScheduleService.validSeriesScore(week, score[0], score[1]));
      for (int[] score : new int[][] { { 3, 0 }, { 0, 3 }, { 2, 2 }, { 3, 1 }, { 1, 1 } })
        assertFalse(ScheduleService.validSeriesScore(week, score[0], score[1]));
    }
  }

  @Test
  void weekTwoZeroThreeIsSavedAndInvalidScoreIsRejected() {
    TeamRepository teams = mock(TeamRepository.class);
    MatchupRepository matchups = mock(MatchupRepository.class);
    LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
    ScheduleService service = new ScheduleService(teams, matchups, settings,
        mock(WeekLockService.class));
    Team home = new Team("Home", "Coach", "A", 1), away = new Team("Away", "Coach", "B", 2);
    org.springframework.test.util.ReflectionTestUtils.setField(home, "id", 1L);
    org.springframework.test.util.ReflectionTestUtils.setField(away, "id", 2L);
    Matchup m = new Matchup(2, 1L, 2L);
    when(matchups.findById(2L)).thenReturn(Optional.of(m));
    when(matchups.findAll()).thenReturn(List.of(m));
    when(teams.findAllMetadata()).thenReturn(List.of(home, away));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,
        () -> service.setResult(2L, 3, 1));
    service.setResult(2L, 0, 3);
    assertEquals(3, away.getDifferential());
    assertEquals(1, away.getWins());
    assertEquals(1, home.getLosses());
    assertEquals(0, m.getHomeScore());
    assertEquals(3, m.getAwayScore());
  }

  @Test
  void invalidScoresDoNotSave() {
    MatchupRepository matchups = mock(MatchupRepository.class);
    ScheduleService service = new ScheduleService(mock(TeamRepository.class), matchups,
        mock(LeagueSettingsRepository.class), mock(WeekLockService.class));
    when(matchups.findById(2L)).thenReturn(Optional.of(new Matchup(2, 1L, 2L)));
    when(matchups.findById(14L)).thenReturn(Optional.of(new Matchup(14, 1L, 2L)));
    for (int[] bad : new int[][] { { 3, 1 }, { 3, 2 }, { 2, 0 }, { 2, 2 }, { -1, 4 } })
      assertThrows(org.springframework.web.server.ResponseStatusException.class,
          () -> service.setResult(2L, bad[0], bad[1]));
    for (int[] bad : new int[][] { { 3, 0 }, { 0, 3 }, { 2, 2 }, { 1, 1 } })
      assertThrows(org.springframework.web.server.ResponseStatusException.class,
          () -> service.setResult(14L, bad[0], bad[1]));
    verify(matchups, never()).save(any(Matchup.class));
  }
}
