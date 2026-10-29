package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.WeekLockService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WeekLockServiceTest {
  private final WeekLockRepository locks = mock(WeekLockRepository.class);
  private final MatchupRepository matchups = mock(MatchupRepository.class);
  private final WeekLockService service = new WeekLockService(locks, matchups);

  @Test
  void completedWeekCanBeLockedThenProtected() {
    List<Matchup> games = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      Matchup game = new Matchup(1, (long) i + 1, (long) i + 9);
      game.setPlayed(true);
      game.setHomeScore(2);
      game.setAwayScore(1);
      games.add(game);
    }
    when(matchups.findByWeekOrderByIdAsc(1)).thenReturn(games);
    service.setLocked(1, true);
    verify(locks).save(argThat(row -> row.getWeek() == 1 && row.isLocked()));

    when(locks.findById(1)).thenReturn(Optional.of(new WeekLock(1, true)));
    ResponseStatusException error =
        assertThrows(ResponseStatusException.class, () -> service.requireEditable(1));
    assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

    service.setLocked(1, false);
    verify(locks).save(argThat(row -> row.getWeek() == 1 && !row.isLocked()));
  }

  @Test
  void cannotLockUnfinishedWeekOrBye() {
    when(matchups.findByWeekOrderByIdAsc(2)).thenReturn(List.of(new Matchup(2, 1L, 2L)));
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(ResponseStatusException.class, () -> service.setLocked(2, true))
            .getStatusCode());
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(ResponseStatusException.class, () -> service.setLocked(4, true))
            .getStatusCode());
    verify(locks, never()).save(any());
  }

  @Test
  void cannotEditOlderResultsUnderLockedPlayoffWeeks() {
    when(locks.findById(14)).thenReturn(Optional.of(new WeekLock(14, true)));
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(ResponseStatusException.class, () -> service.requireEditable(2))
            .getStatusCode());
    assertDoesNotThrow(() -> service.requireEditable(15));
  }

  @Test
  void noLocksExistUntilCommissionerSetsThem() {
    assertFalse(service.isLocked(1));
    assertEquals(16, service.statuses().size());
  }

  @Test
  void cannotLockWeekWithInvalidBestOfThreeScores() {
    List<Matchup> games = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      Matchup game = new Matchup(1, (long) i + 1, (long) i + 9);
      game.setPlayed(true);
      game.setHomeScore(i == 0 ? 3 : 2);
      game.setAwayScore(0);
      games.add(game);
    }
    when(matchups.findByWeekOrderByIdAsc(1)).thenReturn(games);
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(ResponseStatusException.class, () -> service.setLocked(1, true))
            .getStatusCode());
    verify(locks, never()).save(any());
  }
}
