package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pokemondraft.leagueportal.controllers.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.AuthService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CommissionerReminderControllerTest {
  private final AuthService auth=mock(AuthService.class);
  private final LeagueSettingsRepository settings=mock(LeagueSettingsRepository.class);
  private final DraftPickRepository picks=mock(DraftPickRepository.class);
  private final MatchupRepository matchups=mock(MatchupRepository.class);
  private final WeekLockRepository locks=mock(WeekLockRepository.class);
  private final TransactionRecordRepository transactions=mock(TransactionRecordRepository.class);
  private final CommissionerReminderController service=new CommissionerReminderController(
      auth,settings,picks,matchups,locks,transactions);

  private LeagueSettings start(int week) {
    LeagueSettings league=new LeagueSettings();
    league.setCurrentWeek(week);
    when(settings.findById(1L)).thenReturn(Optional.of(league));
    return league;
  }

  @Test void draftCompletionPromptsForFirstDeadlineWithoutAnyWeekLock() {
    start(1);
    when(picks.count()).thenReturn(160L);
    var notices=service.remindersAt(LocalDate.of(2026,11,2));
    assertTrue(notices.stream().anyMatch(n->n.id().equals("week1:deadline")));
    assertFalse(notices.stream().anyMatch(n->n.action().equals("lock")));
  }

  @Test void allEightResultsPromptForLockButNotWeekAdvance() {
    start(1);
    List<Matchup> games=new ArrayList<>();
    for(int i=0;i<8;i++) {
      Matchup g=new Matchup(1,(long)i+1,(long)i+9);
      g.setPlayed(true);g.setHomeScore(2);g.setAwayScore(1);
      games.add(g);
    }
    when(matchups.findByWeekOrderByIdAsc(1)).thenReturn(games);
    var notices=service.remindersAt(LocalDate.of(2026,11,8));
    assertTrue(notices.stream().anyMatch(n->n.id().equals("week:1:lock")));
    assertFalse(notices.stream().anyMatch(n->n.id().equals("week:1:advance")));
    when(locks.findById(1)).thenReturn(Optional.of(new WeekLock(1,true)));
    notices=service.remindersAt(LocalDate.of(2026,11,8));
    assertFalse(notices.stream().anyMatch(n->n.id().equals("week:1:lock")));
    assertTrue(notices.stream().anyMatch(n->n.id().equals("week:1:advance")));
    assertTrue(notices.stream().anyMatch(n->n.id().equals("week:2:deadline")));
  }

  @Test void settingNextDeadlineDismissesOnlyDeadlineReminder() {
    LeagueSettings league=start(1);
    when(locks.findById(1)).thenReturn(Optional.of(new WeekLock(1,true)));
    league.setLineupDeadline(LocalDateTime.of(2026,11,15,20,0));
    var notices=service.remindersAt(LocalDate.of(2026,11,11));
    assertTrue(notices.stream().anyMatch(n->n.id().equals("week:1:advance")));
    assertFalse(notices.stream().anyMatch(n->n.id().equals("week:2:deadline")));
  }

  @Test void eachWaiverPeriodPromptsDayBeforeUntilProcessed() {
    start(4);
    assertFalse(service.remindersAt(LocalDate.of(2026,11,26)).stream()
        .anyMatch(n->n.id().equals("faab:period:1")));
    assertTrue(service.remindersAt(LocalDate.of(2026,11,27)).stream()
        .anyMatch(n->n.id().equals("faab:period:1")));
    when(transactions.countByType("WAIVER_PERIOD_PROCESSED")).thenReturn(1L);
    assertTrue(service.remindersAt(LocalDate.of(2026,11,30)).stream()
        .anyMatch(n->n.id().equals("faab:period:2")));
    assertFalse(service.remindersAt(LocalDate.of(2026,11,30)).stream()
        .anyMatch(n->n.id().equals("faab:period:1")));
    when(transactions.countByType("WAIVER_PERIOD_PROCESSED")).thenReturn(2L);
    assertFalse(service.remindersAt(LocalDate.of(2026,12,1)).stream()
        .anyMatch(n->n.action().equals("faab")));
  }

  @Test void noDeadlineForWeekFourByeOrWeekEight() {
    start(4);
    assertFalse(service.remindersAt(LocalDate.of(2026,11,26)).stream()
        .anyMatch(n->n.action().equals("deadline")));
    start(8);
    assertFalse(service.remindersAt(LocalDate.of(2026,12,26)).stream()
        .anyMatch(n->n.action().equals("deadline")));
  }
}
