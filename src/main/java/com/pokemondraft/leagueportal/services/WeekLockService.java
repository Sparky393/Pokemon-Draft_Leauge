package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.Matchup;
import com.pokemondraft.leagueportal.entity.WeekLock;
import com.pokemondraft.leagueportal.repositories.MatchupRepository;
import com.pokemondraft.leagueportal.repositories.WeekLockRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Commissioner-controlled persistent result locks, enforced in ScheduleService. */
@Service
public class WeekLockService {
  private final WeekLockRepository locks;
  private final MatchupRepository matchups;

  public WeekLockService(WeekLockRepository locks, MatchupRepository matchups) {
    this.locks = locks;
    this.matchups = matchups;
  }

  @Transactional(readOnly = true)
  public boolean isLocked(int week) {
    return locks.findById(week).map(WeekLock::isLocked).orElse(false);
  }

  @Transactional(readOnly = true)
  public Map<Integer, Boolean> statuses() {
    Map<Integer, Boolean> out = new LinkedHashMap<>();
    for (int i = 1; i <= SeasonCalendar.FINAL_WEEK; i++) out.put(i, isLocked(i));
    return out;
  }

  @Transactional
  public void setLocked(int week, boolean locked) {
    if (week < 1 || week > SeasonCalendar.FINAL_WEEK)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid week");

    if (locked) {
      if (!SeasonCalendar.week(week).lineupRequired())
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "There are no games to lock in this week");
      List<Matchup> games = matchups.findByWeekOrderByIdAsc(week);
      int expected =
          SeasonCalendar.isRegularGameWeek(week) ? 8 : week == 14 ? 4 : week == 15 ? 2 : 1;
      if (games.size() != expected
          || games.stream()
              .anyMatch(
                  g ->
                      !g.isPlayed()
                          || !ScheduleService.validSeriesScore(g.getHomeScore(), g.getAwayScore())))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "All "
                + expected
                + " results for Week "
                + week
                + " must be valid best-of-three scores (2–0, 2–1, 0–2, 1–2).");
    }
    WeekLock row = locks.findById(week).orElse(new WeekLock(week, locked));
    row.setLocked(locked);
    locks.save(row);
  }

  /**
   * Any later locked playoff round depends on earlier results, including regular-season seeds. Do
   * not allow changing an earlier result underneath a finalized playoff bracket.
   */
  @Transactional(readOnly = true)
  public void requireEditable(int week) {
    if (isLocked(week))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Week " + week + " is locked. Unlock it in Commissioner to edit scores.");
    for (int later = Math.max(14, week + 1); later <= SeasonCalendar.FINAL_WEEK; later++)
      if (isLocked(later))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Week " + later + " is locked. Unlock later playoff weeks before editing Week " + week);
  }
}
