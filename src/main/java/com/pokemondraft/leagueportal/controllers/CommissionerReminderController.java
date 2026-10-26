package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.*;
import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only actionable commissioner reminders for this single season. Derived from saved state; no
 * automated week transitions or bid processing.
 */
@RestController
@RequestMapping("/api/manager/reminders")
public class CommissionerReminderController {
  private static final ZoneId EASTERN = ZoneId.of("America/New_York");
  private final AuthService auth;
  private final LeagueSettingsRepository settings;
  private final DraftPickRepository picks;
  private final MatchupRepository matchups;
  private final WeekLockRepository locks;
  private final TransactionRecordRepository transactions;

  public CommissionerReminderController(AuthService auth, LeagueSettingsRepository settings,
      DraftPickRepository picks, MatchupRepository matchups, WeekLockRepository locks,
      TransactionRecordRepository transactions) {
    this.auth = auth;
    this.settings = settings;
    this.picks = picks;
    this.matchups = matchups;
    this.locks = locks;
    this.transactions = transactions;
  }

  public record Reminder(String id, String title, String message, String action) {
  }

  private static LocalDate gameWeekStart(int week) {
    return switch (week) {
    case 1 -> LocalDate.of(2026, 11, 6);
    case 2 -> LocalDate.of(2026, 11, 13);
    case 3 -> LocalDate.of(2026, 11, 19);
    case 5 -> LocalDate.of(2026, 12, 4);
    case 6 -> LocalDate.of(2026, 12, 11);
    case 7 -> LocalDate.of(2026, 12, 18);
    case 10 -> LocalDate.of(2027, 1, 8);
    case 11 -> LocalDate.of(2027, 1, 15);
    case 12 -> LocalDate.of(2027, 1, 22);
    case 13 -> LocalDate.of(2027, 1, 29);
    case 14 -> LocalDate.of(2027, 2, 5);
    case 15 -> LocalDate.of(2027, 2, 12);
    case 16 -> LocalDate.of(2027, 2, 19);
    default -> null;
    };
  }

  private static boolean needsDeadline(LeagueSettings league, int week) {
    LocalDate start = gameWeekStart(week);
    if (start == null)
      return false; // Waiver and bye weeks do not need lineups.
    LocalDateTime deadline = league.getLineupDeadline();
    // A deadline from a previous week does not count for the next week.
    return deadline == null || deadline.toLocalDate().isBefore(start);
  }

  public List<Reminder> remindersAt(LocalDate today) {
    LeagueSettings league = settings.findById(1L).orElseGet(LeagueSettings::new);
    int week = league.getCurrentWeek();
    List<Reminder> result = new ArrayList<>();

    // No Week 0 exists; the draft finishing triggers the Week 1 deadline prompt.
    if (week == 1 && (picks.count() >= 160 || league.isDraftFinalized())
        && needsDeadline(league, 1)) {
      result.add(new Reminder("week1:deadline", "Set Week 1 lineup deadline",
          "The draft is complete. Set the Week 1 lineup deadline before coaches submit.",
          "deadline"));
    }

    if (week >= 1 && week <= SeasonCalendar.FINAL_WEEK
        && SeasonCalendar.week(week).lineupRequired()) {
      var games = matchups.findByWeekOrderByIdAsc(week);
      int expected = SeasonCalendar.isRegularGameWeek(week) ? 8
          : week == 14 ? 4 : week == 15 ? 2 : 1;
      boolean locked = locks.findById(week).map(WeekLock::isLocked).orElse(false);
      boolean complete = games.size() == expected && games.stream().allMatch(m -> m.isPlayed()
          && ScheduleService.validSeriesScore(m.getHomeScore(), m.getAwayScore()));
      if (complete && !locked)
        result.add(new Reminder(
            "week:" + week + ":lock", "Lock Week " + week + " results", "All " + expected
                + " games have valid scores. Lock Week " + week + " in League Season Controls.",
            "lock"));
      if (locked && week < SeasonCalendar.FINAL_WEEK) {
        result.add(new Reminder("week:" + week + ":advance", "Advance to Week " + (week + 1),
            "Week " + week + " is locked. Update the current week in League Settings.", "week"));
        int next = week + 1;
        if (needsDeadline(league, next))
          result.add(
              new Reminder("week:" + next + ":deadline", "Set Week " + next + " lineup deadline",
                  "Set the upcoming Week " + next
                      + " deadline in League Settings. This is separate from advancing the week.",
                  "deadline"));
      }
    }
    // After advancing into a game week without entering that week's deadline,
    // the reminder remains actionable. Off weeks never demand a deadline.
    if (week > 1 && needsDeadline(league, week))
      result.add(new Reminder("week:" + week + ":deadline", "Set Week " + week + " lineup deadline",
          "Week " + week + " is active but its lineup deadline has not been set.", "deadline"));

    // Both published waiver-processing times are midnight Eastern. Remind
    // on the preceding calendar day, and continue until the period is processed.
    long processed = transactions.countByType("WAIVER_PERIOD_PROCESSED");
    LocalDate[] processingDays = { LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 1) };
    for (int i = 0; i < processingDays.length; i++) {
      LocalDate firstReminder = processingDays[i].minusDays(1);
      if (processed == i && !today.isBefore(firstReminder)) {
        int period = i + 1;
        result.add(new Reminder("faab:period:" + period, "Process FAAB Period " + period,
            "Period " + period + " bidding closes at 7:00 PM Eastern on " + processingDays[i]
                + ". Process bids manually after 8:00 PM Eastern, and by 11:59 PM Eastern that"
                + " same day"
                + (week != 4 ? ". The commissioner must set the league to Week 4 first." : "."),
            "faab"));
      }
    }
    return result;
  }

  @GetMapping
  public List<Reminder> reminders(HttpSession session) {
    auth.manager(session);
    return remindersAt(LocalDate.now(EASTERN));
  }
}
