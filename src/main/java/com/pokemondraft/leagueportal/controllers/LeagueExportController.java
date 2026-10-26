package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.AuthService;
import com.pokemondraft.leagueportal.services.ScheduleService;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Read-only CSV downloads; no schema changes or league record updates. */
@RestController
@RequestMapping("/api/exports")
public class LeagueExportController {
  private final AuthService auth;
  private final TeamRepository teams;
  private final PokemonRepository pokemon;
  private final RosterEntryRepository roster;
  private final DraftPickRepository picks;
  private final MatchupRepository matchups;
  private final ScheduleService schedule;
  private final TransactionRecordRepository transactions;

  public LeagueExportController(AuthService auth, TeamRepository teams, PokemonRepository pokemon,
      RosterEntryRepository roster, DraftPickRepository picks, MatchupRepository matchups,
      ScheduleService schedule, TransactionRecordRepository transactions) {
    this.auth = auth;
    this.teams = teams;
    this.pokemon = pokemon;
    this.roster = roster;
    this.picks = picks;
    this.matchups = matchups;
    this.schedule = schedule;
    this.transactions = transactions;
  }

  private static String cell(Object value) {
    String s = value == null ? "" : String.valueOf(value);
    // Quoted CSV still executes spreadsheet formulas if opened in Excel.
    if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0)
      s = "'" + s;
    return "\"" + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
  }

  private static void row(StringBuilder b, Object... values) {
    for (int i = 0; i < values.length; i++) {
      if (i > 0)
        b.append(',');
      b.append(cell(values[i]));
    }
    b.append("\r\n");
  }

  @GetMapping(value = "/{type}", produces = "text/csv")
  public ResponseEntity<byte[]> export(@PathVariable String type, HttpSession session) {
    auth.current(session);
    if (!Set.of("draft", "rosters", "standings", "schedule", "transactions").contains(type))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown export type.");
    Map<Long, Team> teamNames = new HashMap<>();
    if (Set.of("draft", "rosters", "schedule").contains(type))
      for (Team t : teams.findAllMetadata())
        teamNames.put(t.getId(), t);
    List<DraftPick> draftRows = type.equals("draft") ? picks.findAllByOrderByOverallPickAsc()
        : List.of();
    List<RosterEntry> rosterRows = type.equals("rosters") ? roster.findAll() : List.of();
    Set<Long> ids = new HashSet<>();
    for (DraftPick pick : draftRows)
      ids.add(pick.getPokemonId());
    for (RosterEntry entry : rosterRows)
      ids.add(entry.getPokemonId());
    Map<Long, Pokemon> pokemonNames = new HashMap<>();
    if (!ids.isEmpty())
      for (Pokemon p : pokemon.findAllById(ids))
        pokemonNames.put(p.getId(), p);
    StringBuilder csv = new StringBuilder();
    switch (type) {
    case "draft" -> {
      row(csv, "Overall Pick", "Round", "Team", "Pokemon", "Pokemon Price", "Picked At");
      for (DraftPick p : draftRows) {
        Team t = teamNames.get(p.getTeamId());
        Pokemon mon = pokemonNames.get(p.getPokemonId());
        row(csv, p.getOverallPick(), p.getRoundNumber(), t == null ? "Unknown Team" : t.getName(),
            mon == null ? "Unknown Pokemon" : mon.getName(), mon == null ? "" : mon.getPrice(),
            p.getPickedAt());
      }
    }
    case "rosters" -> {
      row(csv, "Division", "Team", "Coach", "Pokemon", "Pokemon Price", "Team Roster Value",
          "FAAB");
      for (Team t : teamNames.values().stream()
          .sorted(Comparator.comparing(Team::getDivision).thenComparing(Team::getName)).toList()) {
        var entries = rosterRows.stream().filter(e -> t.getId().equals(e.getTeamId())).toList();
        int total = entries.stream()
            .mapToInt(e -> pokemonNames.containsKey(e.getPokemonId())
                ? pokemonNames.get(e.getPokemonId()).getPrice()
                : 0)
            .sum();
        if (entries.isEmpty())
          row(csv, t.getDivision(), t.getName(), t.getCoachName(), "", "", 0, t.getFaBudget());
        for (RosterEntry e : entries) {
          Pokemon mon = pokemonNames.get(e.getPokemonId());
          row(csv, t.getDivision(), t.getName(), t.getCoachName(),
              mon == null ? "Unknown Pokemon" : mon.getName(), mon == null ? "" : mon.getPrice(),
              total, t.getFaBudget());
        }
      }
    }
    case "standings" -> {
      row(csv, "Division", "Rank", "Team", "Wins", "Losses", "Differential", "FAAB", "Manual Rank");
      Map<String, List<Team>> standings = schedule.standings();
      for (String division : List.of("A", "B")) {
        int rank = 0;
        for (Team t : standings.get(division))
          row(csv, division, ++rank, t.getName(), t.getWins(), t.getLosses(), t.getDifferential(),
              t.getFaBudget(), t.getManualRank());
      }
    }
    case "schedule" -> {
      row(csv, "Week", "Home Team", "Away Team", "Home Score", "Away Score", "Played");
      for (Matchup m : matchups.findPublished(null, null)) {
        if (m.getWeek() < 1)
          continue;
        Team home = teamNames.get(m.getHomeTeamId()), away = teamNames.get(m.getAwayTeamId());
        row(csv, m.getWeek(), home == null ? "Unknown" : home.getName(),
            away == null ? "Unknown" : away.getName(), m.getHomeScore(), m.getAwayScore(),
            m.isPlayed());
      }
    }
    case "transactions" -> {
      row(csv, "Date", "Type", "Description");
      for (TransactionRecord t : transactions.findAllByOrderByCreatedAtDesc())
        row(csv, t.getCreatedAt(), t.getType(), t.getDescription());
    }
    default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown export.");
    }
    byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"league-" + type + ".csv\"")
        .cacheControl(CacheControl.noStore()).body(bytes);
  }
}
