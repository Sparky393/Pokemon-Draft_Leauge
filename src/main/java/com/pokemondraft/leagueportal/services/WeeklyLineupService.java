package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WeeklyLineupService {
  private final WeeklySubmissionRepository submissions;
  private final RosterEntryRepository roster;
  private final MatchupRepository matchups;
  private final LeagueSettingsRepository settings;
  private final PokemonRepository pokemon;

  public WeeklyLineupService(
      WeeklySubmissionRepository submissions,
      RosterEntryRepository roster,
      MatchupRepository matchups,
      LeagueSettingsRepository settings,
      PokemonRepository pokemon) {
    this.submissions = submissions;
    this.roster = roster;
    this.matchups = matchups;
    this.settings = settings;
    this.pokemon = pokemon;
  }

  @Transactional
  public WeeklySubmission submit(Long teamId, List<Long> ids) {
    LeagueSettings s = settings.findById(1L).orElseThrow();
    int week = s.getCurrentWeek();
    if (!SeasonCalendar.week(week).lineupRequired())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There are no lineup submissions in Week " + week + ". Choose a scheduled game week.");
    finalizeExpired(week);
    if (s.getLineupDeadline() != null && !LocalDateTime.now(ZoneId.of("America/New_York")).isBefore(s.getLineupDeadline()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Week " + week + " lineup deadline has passed. Your lineup can no longer be changed.");
    if (ids == null || ids.size() != 6 || new HashSet<>(ids).size() != 6)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select exactly 6 different Pokémon from your roster.");
    Matchup m = matchupFor(teamId, week);
    Long opponent = m.getHomeTeamId().equals(teamId) ? m.getAwayTeamId() : m.getHomeTeamId();
    if (submissions.findByTeamIdAndWeek(opponent, week).isPresent()
        && submissions.findByTeamIdAndWeek(teamId, week).isPresent())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Both coaches have submitted their Week " + week + " lineups. This matchup is locked.");
    Set<Long> mine =
        roster.findByTeamId(teamId).stream()
            .map(RosterEntry::getPokemonId)
            .collect(Collectors.toSet());
    if (!mine.containsAll(ids))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "At least one selected Pokémon is no longer on your team. Refresh your roster and choose again.");
    String csv = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    WeeklySubmission ws =
        submissions
            .findByTeamIdAndWeek(teamId, week)
            .orElse(new WeeklySubmission(teamId, week, csv));
    ws.setPokemonIdsCsv(csv);
    ws.setSubmittedAt(LocalDateTime.now(ZoneId.of("America/New_York")));
    ws.setAutomatic(false);
    return submissions.save(ws);
  }

  @Transactional
  public void finalizeExpired(int week) {
    if (week < 1 || week > SeasonCalendar.FINAL_WEEK
        || !SeasonCalendar.week(week).lineupRequired()) return;
    LeagueSettings s = settings.findById(1L).orElse(null);
    if (s == null
        || s.getCurrentWeek() != week
        || s.getLineupDeadline() == null
        || LocalDateTime.now(ZoneId.of("America/New_York")).isBefore(s.getLineupDeadline())) return;
    for (Matchup m : matchups.findAllByOrderByWeekAscIdAsc())
      if (m.getWeek() == week) {
        ensureAutomatic(m.getHomeTeamId(), week);
        ensureAutomatic(m.getAwayTeamId(), week);
      }
  }

  private void ensureAutomatic(Long teamId, int week) {
    if (submissions.findByTeamIdAndWeek(teamId, week).isPresent()) return;
    List<Long> owned = roster.findByTeamId(teamId).stream()
        .map(RosterEntry::getPokemonId).distinct().sorted().toList();
    if (owned.size() < 6)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Team " + teamId + " has fewer than 6 Pokémon and cannot be assigned an automatic lineup");

    Set<Long> selected = new LinkedHashSet<>();
    // Carry forward the most recent lineup, skipping non-playing weeks.
    submissions.findAll().stream()
        .filter(ws -> teamId.equals(ws.getTeamId()) && ws.getWeek() < week)
        .max(Comparator.comparingInt(WeeklySubmission::getWeek))
        .ifPresent(previous -> {
          for (String id : previous.getPokemonIdsCsv().split(",")) {
            try {
              Long pokemonId = Long.valueOf(id.trim());
              if (owned.contains(pokemonId) && selected.size() < 6) selected.add(pokemonId);
            } catch (NumberFormatException ignored) {
              // Ignore malformed historical entries rather than failing automatic lineup creation.
            }
          }
        });
    // Replace departed Pokémon, or fill a first-ever lineup, in stable ID order.
    for (Long id : owned) {
      if (selected.size() == 6) break;
      selected.add(id);
    }
    String csv = selected.stream().map(String::valueOf).collect(Collectors.joining(","));
    WeeklySubmission ws = new WeeklySubmission(teamId, week, csv);
    ws.setAutomatic(true);
    ws.setSubmittedAt(LocalDateTime.now(ZoneId.of("America/New_York")));
    submissions.save(ws);
  }

  public Map<String, Object> matchupLineups(Matchup m) {
    finalizeExpired(m.getWeek());
    Optional<WeeklySubmission> h = submissions.findByTeamIdAndWeek(m.getHomeTeamId(), m.getWeek()),
        a = submissions.findByTeamIdAndWeek(m.getAwayTeamId(), m.getWeek());
    boolean reveal = h.isPresent() && a.isPresent();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("revealed", reveal);
    out.put("homeSubmitted", h.isPresent());
    out.put("awaySubmitted", a.isPresent());
    if (reveal) {
      out.put("homeLineup", names(h.get()));
      out.put("awayLineup", names(a.get()));
      out.put("homeAutomatic", h.get().isAutomatic());
      out.put("awayAutomatic", a.get().isAutomatic());
    }
    return out;
  }

  /**
   * Fetch all submission flags for the published schedule in one repository call,
   * instead of querying twice per game (and once per revealed Pokémon).
   */
  @Transactional
  public Map<Long, Map<String, Object>> matchupLineupsBatch(List<Matchup> games) {
    LeagueSettings s = settings.findById(1L).orElse(null);
    if (s != null) finalizeExpired(s.getCurrentWeek());
    Map<String, WeeklySubmission> submitted = new HashMap<>();
    for (WeeklySubmission ws : submissions.findAll())
      submitted.put(ws.getTeamId() + ":" + ws.getWeek(), ws);
    Map<Long, Pokemon> allPokemon = pokemon.findAll().stream()
        .collect(Collectors.toMap(Pokemon::getId, p -> p));
    Map<Long, Map<String, Object>> result = new HashMap<>();
    for (Matchup game : games) {
      WeeklySubmission h = submitted.get(game.getHomeTeamId() + ":" + game.getWeek());
      WeeklySubmission a = submitted.get(game.getAwayTeamId() + ":" + game.getWeek());
      boolean reveal = h != null && a != null;
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("revealed", reveal);
      details.put("homeSubmitted", h != null);
      details.put("awaySubmitted", a != null);
      if (reveal) {
        details.put("homeLineup", names(h, allPokemon));
        details.put("awayLineup", names(a, allPokemon));
        details.put("homeAutomatic", h.isAutomatic());
        details.put("awayAutomatic", a.isAutomatic());
      }
      result.put(game.getId(), details);
    }
    return result;
  }

  public boolean hasMatchup(Long teamId, int week) {
    return matchups.findAllByOrderByWeekAscIdAsc().stream()
        .anyMatch(m -> m.getWeek() == week
            && (teamId.equals(m.getHomeTeamId()) || teamId.equals(m.getAwayTeamId())));
  }

  public boolean isLocked(Long teamId, int week) {
    Matchup m = matchupFor(teamId, week);
    return submissions.findByTeamIdAndWeek(m.getHomeTeamId(), week).isPresent()
        && submissions.findByTeamIdAndWeek(m.getAwayTeamId(), week).isPresent();
  }

  private Matchup matchupFor(Long teamId, int week) {
    return matchups.findAllByOrderByWeekAscIdAsc().stream()
        .filter(
            m ->
                m.getWeek() == week
                    && (m.getHomeTeamId().equals(teamId) || m.getAwayTeamId().equals(teamId)))
        .findFirst()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Your team has no scheduled matchup in Week " + week));
  }

  private List<Map<String, Object>> names(WeeklySubmission s) {
    Map<Long, Pokemon> all =
        pokemon.findAll().stream().collect(Collectors.toMap(Pokemon::getId, p -> p));
    return names(s, all);
  }

  private List<Map<String, Object>> names(WeeklySubmission s, Map<Long, Pokemon> all) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (String x : s.getPokemonIdsCsv().split(",")) {
      Pokemon p = all.get(Long.valueOf(x));
      if (p != null)
        out.add(Map.of("pokemonId", p.getId(), "name", p.getName(), "price", p.getPrice()));
    }
    return out;
  }
}
