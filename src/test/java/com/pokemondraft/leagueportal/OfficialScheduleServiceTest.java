package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OfficialScheduleServiceTest {
  TeamRepository teams = mock(TeamRepository.class);
  MatchupRepository matchups = mock(MatchupRepository.class);
  LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
  ScheduleService service = new ScheduleService(teams, matchups, settings, mock(WeekLockService.class));
  List<Team> teamRows = new ArrayList<>();
  List<Matchup> rows = new ArrayList<>();
  AtomicLong next = new AtomicLong(100);
  Set<Integer> kanto = Set.of(1,2,4,7,8,14,15,16);

  @BeforeEach void init() {
    for (int slot = 1; slot <= 16; slot++) {
      Team team = new Team("Team " + slot, "Coach " + slot,
          kanto.contains(slot) ? "A" : "B", slot);
      ReflectionTestUtils.setField(team, "id", (long) slot);
      team.setWins(100 - slot);
      teamRows.add(team);
    }
    when(teams.findAll()).thenAnswer(inv -> new ArrayList<>(teamRows));
    when(teams.findByDivision("A")).thenAnswer(inv -> new ArrayList<>(teamRows.stream()
        .filter(t -> t.getDivision().equals("A")).toList()));
    when(teams.findByDivision("B")).thenAnswer(inv -> new ArrayList<>(teamRows.stream()
        .filter(t -> t.getDivision().equals("B")).toList()));
    when(matchups.findAll()).thenAnswer(inv -> new ArrayList<>(rows));
    when(matchups.findAllByOrderByWeekAscIdAsc()).thenAnswer(inv -> rows.stream()
        .sorted(Comparator.comparingInt(Matchup::getWeek).thenComparing(Matchup::getId))
        .toList());
    when(matchups.save(any(Matchup.class))).thenAnswer(inv -> {
      Matchup game = inv.getArgument(0);
      if (game.getId() == null) ReflectionTestUtils.setField(game, "id", next.getAndIncrement());
      if (!rows.contains(game)) rows.add(game);
      return game;
    });
    doAnswer(inv -> { rows.remove(inv.getArgument(0)); return null; })
        .when(matchups).delete(any(Matchup.class));
  }

  @Test void reconciliationRetainsExactResultsAndArchivesWrongWeekResults() {
    Matchup valid = new Matchup(1, 7L, 6L);
    valid.setPlayed(true); valid.setHomeScore(3); valid.setAwayScore(1);
    Matchup obsolete = new Matchup(1, 1L, 2L);
    obsolete.setPlayed(true); obsolete.setHomeScore(2); obsolete.setAwayScore(0);
    matchups.save(valid);
    matchups.save(obsolete);

    service.syncOfficialSchedule();
    assertEquals(80, rows.stream().filter(g -> g.getWeek() > 0 && g.getWeek() <= 13).count());
    assertEquals(1, valid.getWeek());
    assertEquals(3, valid.getHomeScore());
    assertEquals(0, obsolete.getWeek(), "Wrong-week scored game stays in history");
    assertEquals(2, obsolete.getHomeScore(), "Archival must not erase scores");
    assertEquals(0, rows.stream().filter(g -> List.of(4,8,9).contains(g.getWeek())).count());
    assertEquals(0, rows.stream().filter(g -> g.getWeek() >= 14).count());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> bracket() {
    return (List<Map<String, Object>>) service.playoffBracket().get("games");
  }

  private Matchup game(int week, long a, long b) {
    return rows.stream().filter(g -> g.getWeek() == week &&
        Set.of(g.getHomeTeamId(), g.getAwayTeamId()).equals(Set.of(a, b)))
        .findFirst().orElseThrow();
  }

  private void win(int week, long home, long away) {
    Matchup g = game(week, home, away);
    g.setPlayed(true); g.setHomeScore(2); g.setAwayScore(0);
  }

  @Test void bracketRemainsPlaceholdersThenSeedsAndAdvancesAllThreeRounds() {
    assertEquals(7, bracket().size());
    assertTrue(bracket().stream().noneMatch(g -> Boolean.TRUE.equals(g.get("ready"))));

    for (SeasonCalendar.Fixture f : SeasonCalendar.fixtures()) {
      Matchup m = new Matchup(f.week(), (long) f.homeSlot(), (long) f.awaySlot());
      m.setPlayed(true); m.setHomeScore(1); m.setAwayScore(0);
      matchups.save(m);
    }
    // Standings are deliberately fixed for this test. The top four Kanto
    // slots are 1,2,4,7; Johto slots are 3,5,6,9.
    List<Map<String, Object>> seeded = bracket();
    assertEquals(4, seeded.stream().filter(g -> Boolean.TRUE.equals(g.get("ready"))).count());
    assertNotNull(game(14,1L,4L));
    assertNotNull(game(14,2L,7L));
    assertNotNull(game(14,3L,9L));
    assertNotNull(game(14,5L,6L));

    win(14,1L,4L);
    win(14,2L,7L);
    win(14,3L,9L);
    win(14,5L,6L);
    assertEquals(6, bracket().stream().filter(g -> Boolean.TRUE.equals(g.get("ready"))).count());
    assertNotNull(game(15,1L,2L)); // higher surviving Kanto seed vs lower
    assertNotNull(game(15,3L,5L)); // higher surviving Johto seed vs lower

    win(15,1L,2L);
    win(15,3L,5L);
    assertEquals(7, bracket().stream().filter(g -> Boolean.TRUE.equals(g.get("ready"))).count());
    assertNotNull(game(16,1L,3L));
  }

  @Test void changingEarlierWinnerArchivesDownstreamResultWithoutDeletingHistory() {
    for (SeasonCalendar.Fixture f : SeasonCalendar.fixtures()) {
      Matchup m = new Matchup(f.week(), (long) f.homeSlot(), (long) f.awaySlot());
      m.setPlayed(true); m.setHomeScore(1); m.setAwayScore(0);
      matchups.save(m);
    }
    bracket();
    win(14,1L,4L); win(14,2L,7L); win(14,3L,9L); win(14,5L,6L);
    bracket();
    Matchup semifinal = game(15,1L,2L);
    semifinal.setPlayed(true); semifinal.setHomeScore(2); semifinal.setAwayScore(1);

    Matchup quarterfinal = game(14,1L,4L);
    quarterfinal.setPlayed(false); quarterfinal.setHomeScore(null); quarterfinal.setAwayScore(null);
    bracket();
    assertEquals(0, semifinal.getWeek());
    assertEquals(2, semifinal.getHomeScore());
    assertFalse(bracket().stream().anyMatch(g -> g.get("week").equals(15)
        && Boolean.TRUE.equals(g.get("ready")) && "Kanto".equals(g.get("division"))));
  }
}
