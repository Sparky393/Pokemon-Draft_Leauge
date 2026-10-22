package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.dto.LineupPokemonDto;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.mapper.LeagueMapper;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/flow")
public class LeagueFlowController {
  private final AuthService auth;
  private final WeeklyLineupService lineups;
  private final MatchupRepository matchups;
  private final TeamRepository teams;
  private final LeagueSettingsRepository settings;
  private final RosterEntryRepository roster;
  private final PokemonRepository pokemon;
  private final FreeAgencyService freeAgency;
  private final WeeklySubmissionRepository submissions;

  public LeagueFlowController(
      AuthService auth,
      WeeklyLineupService lineups,
      MatchupRepository matchups,
      TeamRepository teams,
      LeagueSettingsRepository settings,
      RosterEntryRepository roster,
      PokemonRepository pokemon,
      FreeAgencyService freeAgency,
      WeeklySubmissionRepository submissions) {
    this.auth = auth;
    this.lineups = lineups;
    this.matchups = matchups;
    this.teams = teams;
    this.settings = settings;
    this.roster = roster;
    this.pokemon = pokemon;
    this.freeAgency = freeAgency;
    this.submissions = submissions;
  }

  public record LineupRequest(List<Long> pokemonIds) {}

  @GetMapping("/lineup")
  public Map<String, Object> lineup(HttpSession session) {
    AppUser u = auth.current(session);
    if (u.getRole() == Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager has no lineup");
    LeagueSettings s = settings.findById(1L).orElseThrow();
    int week = s.getCurrentWeek();
    boolean required =
        SeasonCalendar.week(week).lineupRequired() && lineups.hasMatchup(u.getTeamId(), week);
    if (required) lineups.finalizeExpired(week, s);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("week", week);
    m.put("weekLabel", SeasonCalendar.week(week).label());
    m.put("lineupRequired", required);
    m.put("deadline", s.getLineupDeadline());
    m.put("locked", required && lineups.isLocked(u.getTeamId(), week));
    m.put("roster", required ? rosterView(u.getTeamId()) : List.of());
    WeeklySubmission ws =
        required ? submissions.findByTeamIdAndWeek(u.getTeamId(), week).orElse(null) : null;
    m.put("submission", ws);
    if (ws != null)
      m.put(
          "selectedPokemonIds",
          Arrays.stream(ws.getPokemonIdsCsv().split(","))
              .filter(x -> !x.isBlank())
              .map(Long::valueOf)
              .toList());
    else m.put("selectedPokemonIds", List.of());
    return m;
  }

  @PostMapping("/lineup")
  public WeeklySubmission submit(@RequestBody LineupRequest r, HttpSession session) {
    AppUser u = auth.current(session);
    if (u.getRole() == Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager has no lineup");
    return lineups.submit(u.getTeamId(), r.pokemonIds());
  }

  @GetMapping("/calendar")
  public List<Map<String, Object>> calendar(HttpSession session) {
    auth.current(session);
    return SeasonCalendar.weekDetails();
  }

  @GetMapping("/schedule")
  public List<Map<String, Object>> schedule(
      HttpSession session,
      @RequestParam(required = false) Integer week,
      @RequestParam(required = false) Long teamId) {
    auth.current(session);
    List<Matchup> games = matchups.findPublished(week, teamId);
    Set<Long> teamIds = new HashSet<>();
    for (Matchup game : games) {
      teamIds.add(game.getHomeTeamId());
      teamIds.add(game.getAwayTeamId());
    }
    Map<Long, String> names = new HashMap<>();
    if (!teamIds.isEmpty())
      for (TeamRepository.Name t : teams.findNamesByIdIn(teamIds))
        names.put(t.getId(), t.getName());
    Map<Long, Map<String, Object>> lineupsByGame = lineups.matchupLineupsBatch(games);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Matchup x : games) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", x.getId());
      m.put("week", x.getWeek());
      m.put("homeTeamId", x.getHomeTeamId());
      m.put("awayTeamId", x.getAwayTeamId());
      m.put("homeTeam", names.get(x.getHomeTeamId()));
      m.put("awayTeam", names.get(x.getAwayTeamId()));
      m.put("homeScore", x.getHomeScore());
      m.put("awayScore", x.getAwayScore());
      m.put("played", x.isPlayed());
      m.putAll(lineupsByGame.get(x.getId()));
      out.add(m);
    }
    return out;
  }

  private List<LineupPokemonDto> rosterView(Long teamId) {
    List<LineupPokemonDto> out = new ArrayList<>();
    List<Long> ids = roster.findByTeamId(teamId).stream().map(RosterEntry::getPokemonId).toList();
    Map<Long, Pokemon> mons =
        ids.isEmpty()
            ? Map.of()
            : pokemon.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Pokemon::getId, p -> p));
    for (Long id : ids) {
      Pokemon p = mons.get(id);
      if (p != null) out.add(LeagueMapper.lineupPokemon(p));
    }
    return out;
  }
}
