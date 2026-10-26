package com.pokemondraft.leagueportal.services;

import java.util.*;

/**
 * Published Season 2 fixture sheet. Draft positions are stable team identifiers; team names and
 * login names may change without changing opponents.
 *
 * <p>Source correction: the sheet lists Matt3024 twice in Week 2 and omits Burn. Commissioner
 * confirmed Week 2's final row is Burn vs MikeO.
 */
public final class SeasonCalendar {
  private SeasonCalendar() {
  }

  public record Fixture(int week, int homeSlot, int awaySlot) {
  }

  public record WeekInfo(int week, String dates, String label, String kind,
      boolean lineupRequired) {
  }

  // Official team identities from the published schedule. These are lookup
  // labels, not a request to rename rows in the production team table.
  private static final List<String> SLOT_NAMES = List.of("Burn", "Matty/Shuckle", "JackApples",
      "Wide Right", "BryloRen", "TBlocks", "Rover", "ArmTheSharks", "Sparky", "CoachKief", "MikeO",
      "Zandy", "DonnyKong", "Zeke", "Matt3024", "CWC");

  public static String teamNameForSlot(int slot) {
    if (slot < 1 || slot > SLOT_NAMES.size())
      throw new IllegalArgumentException("Invalid team slot " + slot);
    return SLOT_NAMES.get(slot - 1);
  }

  public static final int REGULAR_SEASON_END = 13;
  public static final int FINAL_WEEK = 16;
  private static final List<Integer> GAME_WEEKS = List.of(1, 2, 3, 5, 6, 7, 10, 11, 12, 13);

  // Eight matches per playing week. Each row is [week, sheet's Team 1 slot, Team 2 slot].
  private static final int[][] FIXTURES = { { 1, 7, 6 }, { 1, 2, 9 }, { 1, 4, 11 }, { 1, 16, 12 },
      { 1, 1, 10 }, { 1, 15, 3 }, { 1, 14, 5 }, { 1, 8, 13 }, { 2, 15, 5 }, { 2, 4, 3 },
      { 2, 8, 12 }, { 2, 16, 6 }, { 2, 2, 10 }, { 2, 14, 9 }, { 2, 7, 13 }, { 2, 1, 11 },
      { 3, 1, 6 }, { 3, 2, 5 }, { 3, 4, 12 }, { 3, 8, 3 }, { 3, 7, 9 }, { 3, 15, 13 },
      { 3, 16, 11 }, { 3, 14, 10 }, { 5, 4, 8 }, { 5, 15, 7 }, { 5, 16, 14 }, { 5, 1, 2 },
      { 5, 5, 9 }, { 5, 3, 10 }, { 5, 11, 13 }, { 5, 12, 6 }, { 6, 8, 7 }, { 6, 4, 16 },
      { 6, 2, 15 }, { 6, 14, 1 }, { 6, 9, 10 }, { 6, 5, 11 }, { 6, 6, 3 }, { 6, 13, 12 },
      { 7, 16, 8 }, { 7, 7, 2 }, { 7, 1, 4 }, { 7, 15, 14 }, { 7, 11, 9 }, { 7, 10, 6 },
      { 7, 12, 5 }, { 7, 3, 13 }, { 10, 8, 2 }, { 10, 16, 1 }, { 10, 14, 7 }, { 10, 4, 15 },
      { 10, 3, 9 }, { 10, 13, 5 }, { 10, 10, 12 }, { 10, 6, 11 }, { 11, 1, 8 }, { 11, 2, 14 },
      { 11, 15, 16 }, { 11, 7, 4 }, { 11, 12, 9 }, { 11, 6, 13 }, { 11, 3, 11 }, { 11, 10, 5 },
      { 12, 8, 14 }, { 12, 1, 15 }, { 12, 4, 2 }, { 12, 16, 7 }, { 12, 9, 13 }, { 12, 12, 3 },
      { 12, 5, 6 }, { 12, 11, 10 }, { 13, 15, 8 }, { 13, 14, 4 }, { 13, 7, 1 }, { 13, 2, 16 },
      { 13, 9, 6 }, { 13, 11, 12 }, { 13, 13, 10 }, { 13, 5, 3 } };

  public static List<Fixture> fixtures() {
    List<Fixture> rows = new ArrayList<>(FIXTURES.length);
    for (int[] row : FIXTURES)
      rows.add(new Fixture(row[0], row[1], row[2]));
    return List.copyOf(rows);
  }

  public static int fixtureCount() {
    return FIXTURES.length;
  }

  public static boolean isRegularGameWeek(int week) {
    return GAME_WEEKS.contains(week);
  }

  public static boolean isPlayoffWeek(int week) {
    return week >= 14 && week <= FINAL_WEEK;
  }

  public static WeekInfo week(int number) {
    return weeks().stream().filter(w -> w.week() == number).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid season week: " + number));
  }

  public static List<WeekInfo> weeks() {
    return List.of(new WeekInfo(1, "November 6–10", "Cross-division games", "regular", true),
        new WeekInfo(2, "November 13–17", "Cross-division games", "regular", true),
        new WeekInfo(3, "November 19–24", "Cross-division games", "regular", true),
        new WeekInfo(4, "November 25–December 1", "Free agency — no matches; two waiver periods",
            "free_agency", false),
        new WeekInfo(5, "December 4–8", "Divisional play begins", "regular", true),
        new WeekInfo(6, "December 11–15", "Division games", "regular", true),
        new WeekInfo(7, "December 18–22", "Division games", "regular", true),
        new WeekInfo(8, "December 25–29", "Christmas bye — no matches", "bye", false),
        new WeekInfo(9, "January 1–5", "New Year's bye — no matches", "bye", false),
        new WeekInfo(10, "January 8–12", "Division games", "regular", true),
        new WeekInfo(11, "January 15–19", "Division games", "regular", true),
        new WeekInfo(12, "January 22–26", "Division games", "regular", true),
        new WeekInfo(13, "January 29–February 2", "Final regular-season games", "regular", true),
        new WeekInfo(14, "February 5–9", "Playoffs — quarterfinals", "playoffs", true),
        new WeekInfo(15, "February 12–16", "Playoffs — divisional semifinals", "playoffs", true),
        new WeekInfo(16, "February 19–23", "Playoffs — championship final", "playoffs", true));
  }

  public static List<Map<String, Object>> weekDetails() {
    return weeks().stream().map(w -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("week", w.week());
      item.put("dates", w.dates());
      item.put("label", w.label());
      item.put("kind", w.kind());
      item.put("lineupRequired", w.lineupRequired());
      if (w.week() == 4) {
        item.put("waiverNotes",
            List.of("Period 1: November 25–28 until 7 PM ET; process by end of November 28",
                "Period 2: November 28 after 7 PM–December 1 until 7 PM ET; process by end"
                    + " of December 1"));
      }
      return item;
    }).toList();
  }
}
