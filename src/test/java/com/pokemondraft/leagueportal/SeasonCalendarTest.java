package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import com.pokemondraft.leagueportal.services.SeasonCalendar;
import java.util.*;
import org.junit.jupiter.api.Test;

class SeasonCalendarTest {
  private static final Set<Integer> KANTO = Set.of(1, 2, 4, 7, 8, 14, 15, 16);
  private static final Set<Integer> GAME_WEEKS = Set.of(1, 2, 3, 5, 6, 7, 10, 11, 12, 13);

  @Test void allEightyPublishedMatchesMatchTheSheetRowByRow() {
    // Week 2 correction confirmed by commissioner: Burn (1) plays MikeO (11).
    int[][] expected = {
      {1,7,6},{1,2,9},{1,4,11},{1,16,12},{1,1,10},{1,15,3},{1,14,5},{1,8,13},
      {2,15,5},{2,4,3},{2,8,12},{2,16,6},{2,2,10},{2,14,9},{2,7,13},{2,1,11},
      {3,1,6},{3,2,5},{3,4,12},{3,8,3},{3,7,9},{3,15,13},{3,16,11},{3,14,10},
      {5,4,8},{5,15,7},{5,16,14},{5,1,2},{5,5,9},{5,3,10},{5,11,13},{5,12,6},
      {6,8,7},{6,4,16},{6,2,15},{6,14,1},{6,9,10},{6,5,11},{6,6,3},{6,13,12},
      {7,16,8},{7,7,2},{7,1,4},{7,15,14},{7,11,9},{7,10,6},{7,12,5},{7,3,13},
      {10,8,2},{10,16,1},{10,14,7},{10,4,15},{10,3,9},{10,13,5},{10,10,12},{10,6,11},
      {11,1,8},{11,2,14},{11,15,16},{11,7,4},{11,12,9},{11,6,13},{11,3,11},{11,10,5},
      {12,8,14},{12,1,15},{12,4,2},{12,16,7},{12,9,13},{12,12,3},{12,5,6},{12,11,10},
      {13,15,8},{13,14,4},{13,7,1},{13,2,16},{13,9,6},{13,11,12},{13,13,10},{13,5,3}
    };
    List<SeasonCalendar.Fixture> actual = SeasonCalendar.fixtures();
    assertEquals(80, actual.size());
    for (int i = 0; i < expected.length; i++) {
      assertEquals(new SeasonCalendar.Fixture(expected[i][0],expected[i][1],expected[i][2]),
          actual.get(i), "Fixture row " + (i + 1));
    }
  }

  @Test void everyTeamPlaysOncePerPlayingWeekAndEveryDivisionOpponentOnce() {
    List<SeasonCalendar.Fixture> fixtures = SeasonCalendar.fixtures();
    Set<String> seenPairs = new HashSet<>();
    for (int week : GAME_WEEKS) {
      List<SeasonCalendar.Fixture> games = fixtures.stream().filter(f -> f.week() == week).toList();
      assertEquals(8, games.size(), "Week " + week);
      Set<Integer> participants = new HashSet<>();
      for (SeasonCalendar.Fixture fixture : games) {
        assertNotEquals(fixture.homeSlot(), fixture.awaySlot());
        assertTrue(participants.add(fixture.homeSlot()), "Duplicate team in Week " + week);
        assertTrue(participants.add(fixture.awaySlot()), "Duplicate team in Week " + week);
        String key = Math.min(fixture.homeSlot(), fixture.awaySlot()) + ":" +
            Math.max(fixture.homeSlot(), fixture.awaySlot());
        assertTrue(seenPairs.add(key), "Repeated matchup " + key);
        assertEquals(week <= 3, KANTO.contains(fixture.homeSlot()) != KANTO.contains(fixture.awaySlot()),
            "Wrong cross/division pairing in Week " + week);
      }
      assertEquals(16, participants.size(), "Not all 16 coaches play in Week " + week);
    }
    assertEquals(80, seenPairs.size());
    for (int team = 1; team <= 16; team++) {
      Set<Integer> divisionOpponents = new HashSet<>();
      for (SeasonCalendar.Fixture fixture : fixtures) {
        if (fixture.week() < 5) continue;
        if (fixture.homeSlot() == team) divisionOpponents.add(fixture.awaySlot());
        if (fixture.awaySlot() == team) divisionOpponents.add(fixture.homeSlot());
      }
      Set<Integer> expected = new HashSet<>(KANTO.contains(team)
          ? KANTO : Set.of(3,5,6,9,10,11,12,13));
      expected.remove(team);
      assertEquals(expected, divisionOpponents, "Team " + team + " must face all division teams once");
    }
  }

  @Test void freeAgencyAndByesHaveNoMatchesOrLineups() {
    for (int week : List.of(4, 8, 9)) {
      assertFalse(SeasonCalendar.week(week).lineupRequired());
      assertTrue(SeasonCalendar.fixtures().stream().noneMatch(f -> f.week() == week));
    }
    assertEquals("free_agency", SeasonCalendar.week(4).kind());
    assertEquals("bye", SeasonCalendar.week(8).kind());
    assertEquals("bye", SeasonCalendar.week(9).kind());
    assertEquals(16, SeasonCalendar.weeks().size());
    assertEquals(13, SeasonCalendar.REGULAR_SEASON_END);
    assertEquals(16, SeasonCalendar.FINAL_WEEK);
    assertTrue(SeasonCalendar.week(14).lineupRequired());
  }
}
