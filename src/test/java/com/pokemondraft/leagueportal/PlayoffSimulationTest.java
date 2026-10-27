package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

/** Real playoff service, in-memory Mockito repositories only: no Spring boot or database. */
class PlayoffSimulationTest {
  private static final long FIRST_SEED = 20261024L;
  private static final Set<String> championSeeds = new TreeSet<>();
  private static int randomCases, exhaustiveCases;

  static Stream<Arguments> randomSeasons() {
    return LongStream.range(FIRST_SEED, FIRST_SEED + 500)
        .mapToObj(seed -> Arguments.of(seed, new Random(seed).nextInt(128)));
  }

  static Stream<Arguments> everyPlayoffOutcome() {
    // Seven games, two possible winners each: all 128 paths, including every upset.
    return IntStream.range(0, 128).mapToObj(mask -> Arguments.of(FIRST_SEED, mask));
  }

  @ParameterizedTest(name = "random season {0}, playoff outcome mask {1}")
  @MethodSource("randomSeasons")
  void randomStandingsAdvanceThroughTheWholePlayoff(long seed, int outcomes) {
    simulate(seed, outcomes);
    randomCases++;
  }

  @ParameterizedTest(name = "exhaustive playoff outcome {1}")
  @MethodSource("everyPlayoffOutcome")
  void everyCombinationOfFavoritesAndUpsetsAdvancesCorrectly(long seed, int outcomes) {
    simulate(seed, outcomes);
    exhaustiveCases++;
  }

  @org.junit.jupiter.api.Test
  void quarterfinalsPairFirstFourthAndSecondThirdInBothDivisions() {
    Fixture fixture = new Fixture(FIRST_SEED);
    fixture.service.setResult(fixture.lastRegularId, fixture.lastHomeScore, fixture.lastAwayScore);
    Map<String, List<Team>> standings = fixture.service.standings();
    assertQuarterfinals(
        fixture,
        standings.get("A").stream().map(Team::getId).toList(),
        standings.get("B").stream().map(Team::getId).toList());
  }

  @org.junit.jupiter.api.Test
  void exportRealBackendBracketSnapshotsForDesktopAndMobileTests() throws Exception {
    List<Map<String, Object>> scenarios = new ArrayList<>();
    for (long seed = FIRST_SEED; seed < FIRST_SEED + 6; seed++) {
      for (int outcome : new int[] {0, 127, new Random(seed).nextInt(128)}) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        simulate(seed, outcome, snapshots);
        assertEquals(9, snapshots.size());
        scenarios.add(Map.of("seed", seed, "outcomeMask", outcome, "snapshots", snapshots));
      }
    }
    java.nio.file.Path output = java.nio.file.Path.of("target/playoff-simulation-snapshots.json");
    java.nio.file.Files.createDirectories(output.getParent());
    java.nio.file.Files.writeString(
        output,
        new tools.jackson.databind.ObjectMapper()
            .writeValueAsString(
                Map.of(
                    "source",
                    "real ScheduleService with mock repositories",
                    "scenarios",
                    scenarios)));
    System.out.println(
        "FRONTEND FIXTURES: "
            + scenarios.size()
            + " backend-verified playoffs exported to "
            + output);
  }

  @AfterAll
  static void reportCoverage() {
    if (randomCases + exhaustiveCases == 0) return;
    if (exhaustiveCases == 128)
      assertEquals(
          Set.of(
              "Kanto #1",
              "Kanto #2",
              "Kanto #3",
              "Kanto #4",
              "Johto #1",
              "Johto #2",
              "Johto #3",
              "Johto #4"),
          championSeeds);
    System.out.println(
        "PLAYOFF SIMULATION: "
            + randomCases
            + " randomized regular seasons + "
            + exhaustiveCases
            + " exhaustive playoff outcome paths; "
            + (randomCases + exhaustiveCases)
            + " complete playoffs, "
            + (7 * (randomCases + exhaustiveCases))
            + " playoff results. Champions covered: "
            + championSeeds);
  }

  private void simulate(long seed, int outcomes) {
    simulate(seed, outcomes, null);
  }

  private void simulate(long seed, int outcomes, List<Map<String, Object>> snapshots) {
    Fixture f = new Fixture(seed);
    f.snapshots = snapshots;
    f.capture(null);
    Random scores = new Random(seed ^ outcomes);
    // Final regular-season result must be present before any quarterfinal is materialized.
    List<Map<String, Object>> placeholders = f.bracket();
    assertTrue(placeholders.stream().noneMatch(g -> Boolean.TRUE.equals(g.get("ready"))));
    assertEquals(80, f.rows.size());
    f.service.setResult(f.lastRegularId, f.lastHomeScore, f.lastAwayScore);
    f.capture(f.lastRegularId);

    Map<String, List<Long>> rankings = new HashMap<>();
    f.service
        .standings()
        .forEach(
            (division, teams) -> rankings.put(division, teams.stream().map(Team::getId).toList()));
    List<Long> kanto = rankings.get("A"), johto = rankings.get("B");
    var regularBefore = f.regularSnapshot();
    var standingsBefore = f.standingsSnapshot();
    List<Map<String, Object>> seeded = f.bracket();
    assertEquals(7, seeded.size());
    assertEquals(4, f.games(14).size());
    assertEquals(0, f.games(15).size());
    assertEquals(0, f.games(16).size());

    assertQuarterfinals(f, kanto, johto);
    assertEquals(
        List.of("1 seed", "4 seed", "2 seed", "3 seed", "1 seed", "4 seed", "2 seed", "3 seed"),
        placeholders.stream()
            .filter(g -> g.get("week").equals(14))
            .flatMap(g -> Stream.of((String) g.get("homeTeam"), (String) g.get("awayTeam")))
            .toList());

    Long k1 = f.play(14, kanto.get(0), kanto.get(3), bit(outcomes, 0), scores);
    assertEquals(0, f.games(15).size(), "One quarterfinal winner is not enough for a semifinal");
    Long k2 = f.play(14, kanto.get(1), kanto.get(2), bit(outcomes, 1), scores);
    assertEquals(1, f.games(15).size(), "Kanto's semifinal appears when both winners are known");
    Long j1 = f.play(14, johto.get(0), johto.get(3), bit(outcomes, 2), scores);
    assertEquals(1, f.games(15).size());
    Long j2 = f.play(14, johto.get(1), johto.get(2), bit(outcomes, 3), scores);
    assertEquals(2, f.games(15).size());

    List<Long> kSurvivors = survivors(kanto, k1, k2), jSurvivors = survivors(johto, j1, j2);
    assertPair(f.game(15, k1, k2), kSurvivors.get(0), kSurvivors.get(1));
    assertPair(f.game(15, j1, j2), jSurvivors.get(0), jSurvivors.get(1));
    assertEquals(0, f.games(16).size());
    Long kWinner = f.play(15, kSurvivors.get(0), kSurvivors.get(1), bit(outcomes, 4), scores);
    assertEquals(0, f.games(16).size(), "Final waits for both division winners");
    Long jWinner = f.play(15, jSurvivors.get(0), jSurvivors.get(1), bit(outcomes, 5), scores);
    assertEquals(1, f.games(16).size());
    assertPair(f.game(16, kWinner, jWinner), kWinner, jWinner);
    Long champion = f.play(16, kWinner, jWinner, bit(outcomes, 6), scores);
    assertEquals(bit(outcomes, 6) ? jWinner : kWinner, champion);
    championSeeds.add(
        kanto.contains(champion)
            ? "Kanto #" + (kanto.indexOf(champion) + 1)
            : "Johto #" + (johto.indexOf(champion) + 1));

    List<Map<String, Object>> finished = f.bracket();
    assertEquals(7, finished.stream().filter(g -> Boolean.TRUE.equals(g.get("played"))).count());
    Map<String, Object> finalView =
        finished.stream().filter(g -> g.get("week").equals(16)).findFirst().orElseThrow();
    assertEquals(f.name(kWinner), finalView.get("homeTeam"));
    assertEquals(f.name(jWinner), finalView.get("awayTeam"));
    assertEquals(
        standingsBefore,
        f.standingsSnapshot(),
        "Playoff scores cannot change regular-season standings");
    assertEquals(
        regularBefore,
        f.regularSnapshot(),
        "Regular match IDs, opponents and scores stay unchanged");
    assertEquals(
        87, f.rows.size(), "Exactly 80 regular games + 4 quarterfinals + 2 semifinals + 1 final");
    var beforeRefresh = f.matchSnapshot();
    assertEquals(finished, f.bracket());
    assertEquals(finished, f.bracket());
    assertEquals(
        beforeRefresh,
        f.matchSnapshot(),
        "Repeated bracket reads must not duplicate or rewrite games");
    verify(f.matchups, never()).delete(any(Matchup.class));
    verify(f.teams, never()).save(any(Team.class));
    verifyNoInteractions(f.settings);
    if (seed == FIRST_SEED && outcomes == new Random(FIRST_SEED).nextInt(128)) {
      System.out.println(
          "SAMPLE seed="
              + seed
              + ", outcomeMask="
              + outcomes
              + "; Kanto seeds="
              + kanto.stream().limit(4).map(f::name).toList()
              + "; Johto seeds="
              + johto.stream().limit(4).map(f::name).toList()
              + "; Kanto semifinal="
              + kSurvivors.stream().map(f::name).toList()
              + "; Johto semifinal="
              + jSurvivors.stream().map(f::name).toList()
              + "; final="
              + f.name(kWinner)
              + " vs "
              + f.name(jWinner)
              + "; champion="
              + f.name(champion));
    }
  }

  private static boolean bit(int mask, int bit) {
    return (mask & (1 << bit)) != 0;
  }

  private static List<Long> survivors(List<Long> seeds, Long first, Long second) {
    return seeds.stream().filter(id -> id.equals(first) || id.equals(second)).toList();
  }

  private static void assertPair(Matchup game, Long home, Long away) {
    assertEquals(home, game.getHomeTeamId());
    assertEquals(away, game.getAwayTeamId());
  }

  private static void assertQuarterfinals(Fixture f, List<Long> kanto, List<Long> johto) {
    for (List<Long> seeds : List.of(kanto, johto)) {
      assertPair(f.game(14, seeds.get(0), seeds.get(3)), seeds.get(0), seeds.get(3));
      assertPair(f.game(14, seeds.get(1), seeds.get(2)), seeds.get(1), seeds.get(2));
    }
    assertEquals(
        8,
        f.games(14).stream()
            .flatMap(g -> Stream.of(g.getHomeTeamId(), g.getAwayTeamId()))
            .distinct()
            .count());
  }

  private record GameState(
      Long id,
      int week,
      Long home,
      Long away,
      Integer homeScore,
      Integer awayScore,
      boolean played) {}

  private record TeamState(
      Long id, int wins, int losses, int differential, int faab, int draftPosition) {}

  private static class Fixture {
    final TeamRepository teams = mock(TeamRepository.class);
    final MatchupRepository matchups = mock(MatchupRepository.class);
    final LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
    final ScheduleService service =
        new ScheduleService(teams, matchups, settings, mock(WeekLockService.class));
    final List<Team> teamRows = new ArrayList<>();
    final Map<Long, Matchup> rows = new LinkedHashMap<>();
    final AtomicLong next = new AtomicLong(10000);
    Long lastRegularId;
    int lastHomeScore, lastAwayScore;
    List<Map<String, Object>> snapshots;

    Fixture(long seed) {
      Random random = new Random(seed);
      Set<Integer> kantoSlots = Set.of(1, 2, 4, 7, 8, 14, 15, 16);
      Map<Integer, Long> slotIds = new HashMap<>();
      List<Long> ids =
          new ArrayList<>(LongStream.range(100, 116).map(id -> id * 17).boxed().toList());
      Collections.shuffle(ids, random);
      for (int slot = 1; slot <= 16; slot++) {
        Team team =
            new Team(
                "Club " + slot, "Test Coach " + slot, kantoSlots.contains(slot) ? "A" : "B", slot);
        ReflectionTestUtils.setField(team, "id", ids.get(slot - 1));
        teamRows.add(team);
        slotIds.put(slot, team.getId());
      }
      Collections.shuffle(teamRows, random);
      when(teams.findAllMetadata()).thenAnswer(inv -> new ArrayList<>(teamRows));
      when(matchups.findAll()).thenAnswer(inv -> new ArrayList<>(rows.values()));
      when(matchups.findById(anyLong()))
          .thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<Long>getArgument(0))));
      when(matchups.save(any(Matchup.class)))
          .thenAnswer(
              inv -> {
                Matchup game = inv.getArgument(0);
                if (game.getId() == null)
                  ReflectionTestUtils.setField(game, "id", next.getAndIncrement());
                rows.put(game.getId(), game);
                return game;
              });
      doAnswer(
              inv -> {
                rows.remove(inv.<Matchup>getArgument(0).getId());
                return null;
              })
          .when(matchups)
          .delete(any(Matchup.class));
      for (SeasonCalendar.Fixture fixture : SeasonCalendar.fixtures()) {
        Matchup game =
            new Matchup(
                fixture.week(), slotIds.get(fixture.homeSlot()), slotIds.get(fixture.awaySlot()));
        int homeScore = random.nextInt(4);
        game.setHomeScore(homeScore);
        game.setAwayScore(3 - homeScore);
        game.setPlayed(true);
        matchups.save(game);
        lastRegularId = game.getId();
      }
      Matchup last = rows.get(lastRegularId);
      lastHomeScore = last.getHomeScore();
      lastAwayScore = last.getAwayScore();
      last.setPlayed(false);
      last.setHomeScore(null);
      last.setAwayScore(null);
      service.recalculateStandings();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> bracket() {
      return (List<Map<String, Object>>) service.playoffBracket().get("games");
    }

    List<Matchup> games(int week) {
      return rows.values().stream().filter(g -> g.getWeek() == week).toList();
    }

    Matchup game(int week, Long first, Long second) {
      return games(week).stream()
          .filter(g -> Set.of(g.getHomeTeamId(), g.getAwayTeamId()).equals(Set.of(first, second)))
          .findFirst()
          .orElseThrow(
              () ->
                  new AssertionError(
                      "Missing week " + week + " pairing " + first + " vs " + second));
    }

    Long play(int week, Long home, Long away, boolean awayWins, Random random) {
      Matchup game = game(week, home, away);
      assertPair(game, home, away);
      service.setResult(
          game.getId(), awayWins ? random.nextInt(2) : 2, awayWins ? 2 : random.nextInt(2));
      capture(game.getId());
      return awayWins ? away : home;
    }

    void capture(Long changedId) {
      if (snapshots == null) return;
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("bracket", service.playoffBracket());
      snapshot.put(
          "teams",
          teamRows.stream()
              .map(
                  t ->
                      Map.of(
                          "id",
                          t.getId(),
                          "name",
                          t.getName(),
                          "division",
                          t.getDivision(),
                          "draftPosition",
                          t.getDraftPosition(),
                          "wins",
                          t.getWins(),
                          "losses",
                          t.getLosses(),
                          "differential",
                          t.getDifferential(),
                          "faBudget",
                          t.getFaBudget()))
              .toList());
      if (changedId != null) {
        Matchup changed = rows.get(changedId);
        snapshot.put(
            "result",
            Map.of(
                "id",
                changedId,
                "homeScore",
                changed.getHomeScore(),
                "awayScore",
                changed.getAwayScore()));
      }
      snapshots.add(snapshot);
    }

    String name(Long id) {
      return teamRows.stream()
          .filter(t -> t.getId().equals(id))
          .findFirst()
          .orElseThrow()
          .getName();
    }

    List<GameState> matchSnapshot() {
      return rows.values().stream()
          .map(
              g ->
                  new GameState(
                      g.getId(),
                      g.getWeek(),
                      g.getHomeTeamId(),
                      g.getAwayTeamId(),
                      g.getHomeScore(),
                      g.getAwayScore(),
                      g.isPlayed()))
          .toList();
    }

    List<GameState> regularSnapshot() {
      return matchSnapshot().stream().filter(g -> g.week() <= 13).toList();
    }

    List<TeamState> standingsSnapshot() {
      return teamRows.stream()
          .map(
              t ->
                  new TeamState(
                      t.getId(),
                      t.getWins(),
                      t.getLosses(),
                      t.getDifferential(),
                      t.getFaBudget(),
                      t.getDraftPosition()))
          .toList();
    }
  }
}
