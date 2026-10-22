package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleService {
  private final TeamRepository teams;
  private final MatchupRepository matchups;
  private final LeagueSettingsRepository settingsRepo;
  private final WeekLockService weekLocks;

  public ScheduleService(
      TeamRepository teams,
      MatchupRepository matchups,
      LeagueSettingsRepository settingsRepo,
      WeekLockService weekLocks) {
    this.teams = teams;
    this.matchups = matchups;
    this.settingsRepo = settingsRepo;
    this.weekLocks = weekLocks;
  }

  /**
   * Reconcile the database with the official 80-game fixture sheet. Preserve scored results only
   * when opponent AND week match; archive played games from obsolete randomized weeks rather than
   * misattribute their results. Unplayed obsolete random games are deleted. Played obsolete games
   * are retained at week 0 as historical records, never silently deleted or counted in standings.
   */
  @Transactional
  public void generate() {
    syncOfficialSchedule();
  }

  @Transactional
  public void syncOfficialSchedule() {
    List<Team> currentTeams = teams.findAllMetadata();
    Map<Integer, Team> bySlot = resolveOfficialTeams(currentTeams);
    if (bySlot.size() != 16) {
      log.warn(
          "Not reconciling schedule: couldn't identify all 16 official teams by name "
              + "or by unique draft position. Existing database schedule remains unchanged.");
      return;
    }

    List<Matchup> unused = new ArrayList<>();
    for (Matchup m : matchups.findAllByOrderByWeekAscIdAsc())
      if (m.getWeek() >= 1 && m.getWeek() <= SeasonCalendar.REGULAR_SEASON_END) unused.add(m);

    for (SeasonCalendar.Fixture f : SeasonCalendar.fixtures()) {
      Long home = Objects.requireNonNull(bySlot.get(f.homeSlot())).getId();
      Long away = Objects.requireNonNull(bySlot.get(f.awaySlot())).getId();
      Matchup candidate =
          unused.stream()
              .filter(m -> m.getWeek() == f.week() && sameTeams(m, home, away))
              .findFirst()
              .orElse(null);
      if (candidate == null)
        candidate =
            unused.stream()
                .filter(m -> !m.isPlayed() && sameTeams(m, home, away))
                .findFirst()
                .orElse(null);
      if (candidate == null) {
        matchups.save(new Matchup(f.week(), home, away));
      } else {
        unused.remove(candidate);
        if (!home.equals(candidate.getHomeTeamId())) {
          Integer priorHome = candidate.getHomeScore();
          candidate.setHomeScore(candidate.getAwayScore());
          candidate.setAwayScore(priorHome);
        }
        candidate.setHomeTeamId(home);
        candidate.setAwayTeamId(away);
        candidate.setWeek(f.week());
        matchups.save(candidate);
      }
    }
    for (Matchup old : unused) {
      if (old.isPlayed()) {
        // Archive superseded results without discarding user-entered scores.
        old.setWeek(0);
        matchups.save(old);
      } else {
        matchups.delete(old);
      }
    }
    recalculateStandings();
    syncPlayoffs();
  }

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ScheduleService.class);

  /**
   * Existing Neon draft_position values need not be unique. Prefer matching the spreadsheet team
   * names (or their stored coach_name), so IDs, roster ownership and Neon team rows stay unchanged.
   * Fresh seed databases still have Team 1..16 and use the unique draft positions as a fallback.
   */
  private Map<Integer, Team> resolveOfficialTeams(List<Team> all) {
    if (all.size() != 16) return Map.of();

    // Persist fixture positions once. Coach-owned display names can then change
    // freely without breaking the published season on subsequent Render restarts.
    Map<Integer, Team> stored = new HashMap<>();
    for (Team t : all) {
      Integer slot = t.getOfficialSlot();
      if (slot == null || slot < 1 || slot > 16 || stored.putIfAbsent(slot, t) != null) {
        stored.clear();
        break;
      }
    }
    if (stored.size() == 16) return stored;

    Map<Integer, Team> named = new HashMap<>();
    Set<Long> allocated = new HashSet<>();
    for (int slot = 1; slot <= 16; slot++) {
      String official = SeasonCalendar.teamNameForSlot(slot);
      List<Team> found =
          all.stream().filter(t -> official.equalsIgnoreCase(t.getCoachName())).toList();
      if (found.isEmpty())
        found = all.stream().filter(t -> official.equalsIgnoreCase(t.getName())).toList();
      if (found.size() != 1 || !allocated.add(found.get(0).getId())) {
        named.clear();
        break;
      }
      named.put(slot, found.get(0));
    }
    if (named.size() == 16) return saveOfficialSlots(named);

    Map<Integer, Team> positions = new HashMap<>();
    for (Team t : all) {
      int slot = t.getDraftPosition();
      if (slot < 1 || slot > 16 || positions.putIfAbsent(slot, t) != null) return Map.of();
    }
    return positions.size() == 16 ? saveOfficialSlots(positions) : Map.of();
  }

  private Map<Integer, Team> saveOfficialSlots(Map<Integer, Team> resolved) {
    for (Map.Entry<Integer, Team> entry : resolved.entrySet()) {
      Team team = entry.getValue();
      if (!entry.getKey().equals(team.getOfficialSlot())) {
        team.setOfficialSlot(entry.getKey());
        teams.updateOfficialSlot(team.getId(), entry.getKey());
      }
    }
    return resolved;
  }

  private boolean sameTeams(Matchup m, Long a, Long b) {
    return (a.equals(m.getHomeTeamId()) && b.equals(m.getAwayTeamId()))
        || (b.equals(m.getHomeTeamId()) && a.equals(m.getAwayTeamId()));
  }

  public static boolean validSeriesScore(Integer home, Integer away) {
    return validSeriesScore(14, home, away);
  }

  public static boolean validSeriesScore(int week, Integer home, Integer away) {
    if (home == null || away == null) return false;
    if (week >= 14) {
      return (home == 2 && (away == 0 || away == 1)) || (away == 2 && (home == 0 || home == 1));
    }
    return home + away == 3 && home >= 0 && away >= 0 && home <= 3 && away <= 3;
  }

  @Transactional
  public void setResult(Long matchupId, int homeScore, int awayScore) {
    Matchup m = matchups.findById(matchupId).orElseThrow();
    weekLocks.requireEditable(m.getWeek());
    if (m.getWeek() < 1
        || m.getWeek() > SeasonCalendar.FINAL_WEEK
        || !SeasonCalendar.week(m.getWeek()).lineupRequired())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "This week has no scheduled match.");
    if (!validSeriesScore(m.getWeek(), homeScore, awayScore))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          m.getWeek() >= 14
              ? "Playoff scores must be 2–0, 2–1, 0–2 or 1–2."
              : "Regular-season scores must be 3–0, 2–1, 1–2 or 0–3.");
    m.setHomeScore(homeScore);
    m.setAwayScore(awayScore);
    m.setPlayed(true);
    matchups.save(m);
    recalculateStandings();
    syncPlayoffs();
  }

  @Transactional
  public void clearResult(Long matchupId) {
    Matchup m = matchups.findById(matchupId).orElseThrow();
    weekLocks.requireEditable(m.getWeek());
    m.setHomeScore(null);
    m.setAwayScore(null);
    m.setPlayed(false);
    matchups.save(m);
    recalculateStandings();
    syncPlayoffs();
  }

  @Transactional
  public void recalculateStandings() {
    List<Team> all = teams.findAllMetadata();
    Map<Long, Team> map = new HashMap<>();
    for (Team t : all) {
      t.setWins(0);
      t.setLosses(0);
      t.setDifferential(0);
      map.put(t.getId(), t);
    }
    for (Matchup m : matchups.findAll()) {
      if (m.getWeek() < 1
          || m.getWeek() > SeasonCalendar.REGULAR_SEASON_END
          || !SeasonCalendar.isRegularGameWeek(m.getWeek())
          || !m.isPlayed()
          || m.getHomeScore() == null
          || m.getAwayScore() == null) continue;
      Team h = map.get(m.getHomeTeamId()), a = map.get(m.getAwayTeamId());
      int diff = m.getHomeScore() - m.getAwayScore();
      h.setDifferential(h.getDifferential() + diff);
      a.setDifferential(a.getDifferential() - diff);
      if (diff > 0) {
        h.setWins(h.getWins() + 1);
        a.setLosses(a.getLosses() + 1);
      } else if (diff < 0) {
        a.setWins(a.getWins() + 1);
        h.setLosses(h.getLosses() + 1);
      }
    }
    for (Team t : all)
      teams.updateStandings(t.getId(), t.getWins(), t.getLosses(), t.getDifferential());
  }

  /** Only completed, correctly scheduled regular-season games qualify teams for playoffs. */
  public boolean isRegularSeasonComplete() {
    return isRegularSeasonComplete(matchups.findAll());
  }

  private boolean isRegularSeasonComplete(List<Matchup> all) {
    List<Matchup> scheduled =
        all.stream().filter(m -> SeasonCalendar.isRegularGameWeek(m.getWeek())).toList();
    return scheduled.size() == SeasonCalendar.fixtureCount()
        && scheduled.stream()
            .allMatch(m -> m.isPlayed() && m.getHomeScore() != null && m.getAwayScore() != null);
  }

  private record BracketSlot(
      int week,
      String division,
      String label,
      Long home,
      Long away,
      String placeholderHome,
      String placeholderAway) {}

  private Matchup existingPlayoffMatch(BracketSlot slot, List<Matchup> all) {
    if (slot.home() == null || slot.away() == null) return null;
    return all.stream()
        .filter(m -> m.getWeek() == slot.week() && sameTeams(m, slot.home(), slot.away()))
        .findFirst()
        .orElse(null);
  }

  private Long winnerOf(BracketSlot slot, List<Matchup> all) {
    Matchup m = existingPlayoffMatch(slot, all);
    if (m == null
        || !m.isPlayed()
        || m.getHomeScore() == null
        || m.getAwayScore() == null
        || m.getHomeScore().equals(m.getAwayScore())) return null;
    return m.getHomeScore() > m.getAwayScore() ? m.getHomeTeamId() : m.getAwayTeamId();
  }

  private List<BracketSlot> bracketSlots(List<Team> teamRows, List<Matchup> all) {
    boolean ready = isRegularSeasonComplete(all);
    Map<String, Integer> h2h = headToHead(all);
    List<Team> a = standings("A", teamRows, h2h), b = standings("B", teamRows, h2h);
    List<BracketSlot> result = new ArrayList<>();
    // Kanto uses 1v3 and 2v4. Johto uses 1v4 and 2v3.
    result.add(
        new BracketSlot(
            14,
            "Kanto",
            "Quarterfinal 1",
            ready ? a.get(0).getId() : null,
            ready ? a.get(2).getId() : null,
            "1 seed",
            "3 seed"));
    result.add(
        new BracketSlot(
            14,
            "Kanto",
            "Quarterfinal 2",
            ready ? a.get(1).getId() : null,
            ready ? a.get(3).getId() : null,
            "2 seed",
            "4 seed"));
    result.add(
        new BracketSlot(
            14,
            "Johto",
            "Quarterfinal 1",
            ready ? b.get(0).getId() : null,
            ready ? b.get(3).getId() : null,
            "1 seed",
            "4 seed"));
    result.add(
        new BracketSlot(
            14,
            "Johto",
            "Quarterfinal 2",
            ready ? b.get(1).getId() : null,
            ready ? b.get(2).getId() : null,
            "2 seed",
            "3 seed"));

    BracketSlot kSemi = semifinal(result.get(0), result.get(1), a, "Kanto", all);
    BracketSlot jSemi = semifinal(result.get(2), result.get(3), b, "Johto", all);
    result.add(kSemi);
    result.add(jSemi);

    Long kWinner = winnerOf(kSemi, all);
    Long jWinner = winnerOf(jSemi, all);
    result.add(
        new BracketSlot(
            16, "Championship", "Final", kWinner, jWinner, "Kanto champion", "Johto champion"));
    return result;
  }

  private BracketSlot semifinal(
      BracketSlot first, BracketSlot second, List<Team> seeds, String division, List<Matchup> all) {
    Long left = winnerOf(first, all), right = winnerOf(second, all);
    if (left == null || right == null)
      return new BracketSlot(
          15, division, "Semifinal", null, null, "Higher remaining seed", "Lower remaining seed");
    Map<Long, Integer> seedRank = new HashMap<>();
    for (int i = 0; i < seeds.size(); i++) seedRank.put(seeds.get(i).getId(), i);
    if (seedRank.get(left) > seedRank.get(right)) {
      Long tmp = left;
      left = right;
      right = tmp;
    }
    return new BracketSlot(
        15, division, "Semifinal", left, right, "Higher remaining seed", "Lower remaining seed");
  }

  /**
   * Populate only rounds whose entrants are determined. Changing an earlier result safely archives
   * downstream played matchups at week zero instead of erasing them.
   */
  @Transactional
  public void syncPlayoffs() {
    syncPlayoffs(teams.findAllMetadata(), new ArrayList<>(matchups.findAll()));
  }

  private void syncPlayoffs(List<Team> teamRows, List<Matchup> all) {
    List<BracketSlot> slots = bracketSlots(teamRows, all);
    Set<Long> retain = new HashSet<>();
    for (BracketSlot slot : slots) {
      if (slot.home() == null || slot.away() == null) continue;
      Matchup game = existingPlayoffMatch(slot, all);
      if (game == null) {
        game = matchups.save(new Matchup(slot.week(), slot.home(), slot.away()));
        all.add(game);
      } else if (!slot.home().equals(game.getHomeTeamId())) {
        Integer priorHome = game.getHomeScore();
        game.setHomeScore(game.getAwayScore());
        game.setAwayScore(priorHome);
        game.setHomeTeamId(slot.home());
        game.setAwayTeamId(slot.away());
        game = matchups.save(game);
      }
      retain.add(game.getId());
    }
    for (Matchup game : new ArrayList<>(all)) {
      if (SeasonCalendar.isPlayoffWeek(game.getWeek()) && !retain.contains(game.getId())) {
        if (game.isPlayed()) {
          game.setWeek(0);
          matchups.save(game);
        } else {
          matchups.delete(game);
          all.remove(game);
        }
      }
    }
  }

  @Transactional
  public Map<String, Object> playoffBracket() {
    List<Team> teamRows = teams.findAllMetadata();
    List<Matchup> all = new ArrayList<>(matchups.findAll());
    syncPlayoffs(teamRows, all);
    boolean ready = isRegularSeasonComplete(all);
    Map<Long, String> names = new HashMap<>();
    for (Team t : teamRows) names.put(t.getId(), t.getName());
    List<Map<String, Object>> games = new ArrayList<>();
    for (BracketSlot slot : bracketSlots(teamRows, all)) {
      Matchup game = existingPlayoffMatch(slot, all);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("week", slot.week());
      row.put("division", slot.division());
      row.put("round", slot.label());
      row.put("homeTeam", slot.home() == null ? slot.placeholderHome() : names.get(slot.home()));
      row.put("awayTeam", slot.away() == null ? slot.placeholderAway() : names.get(slot.away()));
      row.put("homeScore", game == null ? null : game.getHomeScore());
      row.put("awayScore", game == null ? null : game.getAwayScore());
      row.put("id", game == null ? null : game.getId());
      row.put("played", game != null && game.isPlayed());
      row.put("ready", game != null);
      games.add(row);
    }
    long remaining =
        all.stream()
            .filter(m -> SeasonCalendar.isRegularGameWeek(m.getWeek()) && !m.isPlayed())
            .count();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("regularSeasonComplete", ready);
    result.put("regularGamesRemaining", remaining);
    result.put("games", games);
    return result;
  }

  public List<Team> standings(String division) {
    return standings(
        division, teams.findMetadataByDivision(division), headToHead(matchups.findAll()));
  }

  public Map<String, List<Team>> standings() {
    List<Team> teamRows = teams.findAllMetadata();
    Map<String, Integer> h2h = headToHead(matchups.findAll());
    return Map.of("A", standings("A", teamRows, h2h), "B", standings("B", teamRows, h2h));
  }

  private List<Team> standings(String division, List<Team> teamRows, Map<String, Integer> h2h) {
    List<Team> list =
        new ArrayList<>(teamRows.stream().filter(t -> division.equals(t.getDivision())).toList());
    list.sort(
        (x, y) -> {
          if (x.getManualRank() > 0 || y.getManualRank() > 0) {
            int xr = x.getManualRank() > 0 ? x.getManualRank() : Integer.MAX_VALUE;
            int yr = y.getManualRank() > 0 ? y.getManualRank() : Integer.MAX_VALUE;
            if (xr != yr) return Integer.compare(xr, yr);
          }
          if (x.getWins() != y.getWins()) return Integer.compare(y.getWins(), x.getWins());
          if (x.getDifferential() != y.getDifferential())
            return Integer.compare(y.getDifferential(), x.getDifferential());
          int xy = h2h.getOrDefault(key(x.getId(), y.getId()), 0);
          if (xy != 0) return -Integer.compare(xy, 0);
          return x.getName().compareToIgnoreCase(y.getName());
        });
    return list;
  }

  private Map<String, Integer> headToHead(List<Matchup> all) {
    Map<String, Integer> h = new HashMap<>();
    for (Matchup m : all) {
      if (m.getWeek() < 1
          || m.getWeek() > SeasonCalendar.REGULAR_SEASON_END
          || !SeasonCalendar.isRegularGameWeek(m.getWeek())
          || !m.isPlayed()
          || m.getHomeScore() == null
          || m.getAwayScore() == null
          || m.getHomeScore().equals(m.getAwayScore())) continue;
      Long winner = m.getHomeScore() > m.getAwayScore() ? m.getHomeTeamId() : m.getAwayTeamId();
      Long loser = winner.equals(m.getHomeTeamId()) ? m.getAwayTeamId() : m.getHomeTeamId();
      h.put(key(winner, loser), 1);
      h.put(key(loser, winner), -1);
    }
    return h;
  }

  private String key(Long a, Long b) {
    return a + ":" + b;
  }
}
