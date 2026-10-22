package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.dto.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.mapper.LeagueMapper;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.*;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final AuthService auth;
  private final TeamRepository teams;
  private final PokemonRepository pokemon;
  private final RosterEntryRepository roster;
  private final DraftPickRepository picks;
  private final DraftService draft;
  private final MatchupRepository matchups;
  private final ScheduleService schedule;
  private final LeagueSettingsRepository settingsRepo;
  private final WeeklySubmissionRepository submissions;
  private final FreeAgentBidRepository bids;
  private final FreeAgencyService freeAgency;
  private final WeeklyLineupService lineupService;
  private final TransactionHistoryService history;

  public ApiController(
      AuthService auth,
      TeamRepository teams,
      PokemonRepository pokemon,
      RosterEntryRepository roster,
      DraftPickRepository picks,
      DraftService draft,
      MatchupRepository matchups,
      ScheduleService schedule,
      LeagueSettingsRepository settingsRepo,
      WeeklySubmissionRepository submissions,
      FreeAgentBidRepository bids,
      FreeAgencyService freeAgency,
      WeeklyLineupService lineupService,
      TransactionHistoryService history) {
    this.auth = auth;
    this.teams = teams;
    this.pokemon = pokemon;
    this.roster = roster;
    this.picks = picks;
    this.draft = draft;
    this.matchups = matchups;
    this.schedule = schedule;
    this.settingsRepo = settingsRepo;
    this.submissions = submissions;
    this.bids = bids;
    this.freeAgency = freeAgency;
    this.lineupService = lineupService;
    this.history = history;
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(HttpSession session) {
    AppUser u = auth.current(session);
    LeagueSettings s = settings();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("user", LeagueMapper.user(u));
    out.put("settings", settingsMap(s));
    List<Team> teamList = teams.findAllMetadata();
    out.put("teams", teamList);
    if (u.getTeamId() != null) out.put("myRoster", rosterView(u.getTeamId()));
    out.put("currentDraftTeam", draft.currentTeam(teamList, picks.count(), s));
    return out;
  }

  @GetMapping("/teams")
  public List<Team> teams(HttpSession session) {
    auth.current(session);
    return teams.findAllMetadata().stream()
        .sorted(Comparator.comparing(Team::getDivision).thenComparingInt(Team::getDraftPosition))
        .toList();
  }

  public record TeamUpdate(
      String name,
      String coachName,
      String division,
      Integer faBudget,
      Integer draftPosition,
      Integer manualRank) {}

  @PutMapping("/manager/teams/{id}")
  public Team updateTeam(@PathVariable Long id, @RequestBody TeamUpdate r, HttpSession session) {
    auth.manager(session);
    Team t = teams.findById(id).orElseThrow();
    if (r.name() != null && !r.name().isBlank()) t.setName(r.name());
    if (r.coachName() != null) t.setCoachName(r.coachName());
    if (r.division() != null && (r.division().equals("A") || r.division().equals("B")))
      t.setDivision(r.division());
    if (r.faBudget() != null) {
      if (r.faBudget() < 0 || r.faBudget() > 3)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FAAB must be between $0 and $3");
      t.setFaBudget(r.faBudget());
    }
    if (r.draftPosition() != null && r.draftPosition() != t.getDraftPosition()) {
      if (picks.count() > 0 || settings().isDraftFinalized())
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Draft order cannot be changed after the first pick. Your current order is preserved.");
      if (r.draftPosition() < 1 || r.draftPosition() > 16)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Draft position must be from 1 to 16.");
      if (teams.findAllMetadata().stream()
          .anyMatch(
              other -> !other.getId().equals(id) && other.getDraftPosition() == r.draftPosition()))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "That draft position is already assigned to another team. Swap their positions before"
                + " starting the draft.");
      t.setDraftPosition(r.draftPosition());
    }
    if (r.manualRank() != null) t.setManualRank(r.manualRank());
    return teams.save(t);
  }

  @GetMapping("/pokemon")
  public List<Pokemon> allPokemon(HttpSession session) {
    auth.current(session);
    return pokemon.findAllByOrderByNameAsc();
  }

  @GetMapping("/pokemon/free-agents")
  public List<Pokemon> freeAgents(HttpSession session) {
    auth.current(session);
    return pokemon.findByDraftedFalseOrderByNameAsc();
  }

  public record PriceUpdate(int price) {}

  @PutMapping("/manager/pokemon/{id}/price")
  public Pokemon price(@PathVariable Long id, @RequestBody PriceUpdate r, HttpSession session) {
    auth.manager(session);
    if (r.price() < 0 || r.price() > 20)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Pokémon draft prices must be between $0 and $20.");
    Pokemon p = pokemon.findById(id).orElseThrow();
    if (p.getPrice() != r.price() && (picks.count() > 0 || settings().isDraftFinalized()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Pokémon prices are locked once the draft begins. Existing draft and roster values stay"
              + " unchanged.");
    p.setPrice(r.price());
    return pokemon.save(p);
  }

  @GetMapping("/rosters")
  public List<TeamRosterDto> rosters(HttpSession session) {
    auth.current(session);
    Map<Long, List<RosterPokemonDto>> members = rosterViews(roster.findAll());
    return teams.findAllMetadata().stream()
        .sorted(Comparator.comparing(Team::getName))
        .map(t -> new TeamRosterDto(t, members.getOrDefault(t.getId(), List.of())))
        .toList();
  }

  @GetMapping("/rosters/{teamId}")
  public List<RosterPokemonDto> roster(@PathVariable Long teamId, HttpSession session) {
    auth.current(session);
    return rosterView(teamId);
  }

  @GetMapping("/draft")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> draftState(HttpSession session) {
    auth.current(session);
    LeagueSettings league = settings();
    List<Team> teamRows = teams.findAllMetadata();
    List<DraftPick> pickRows = picks.findAllByOrderByOverallPickAsc();
    Map<Long, String> names =
        teamRows.stream().collect(Collectors.toMap(Team::getId, Team::getName));
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("currentTeam", draft.currentTeam(teamRows, pickRows.size(), league));
    m.put("picks", pickViews(pickRows, names));
    Long lastPickId = pickRows.stream().map(DraftPick::getId).max(Long::compareTo).orElse(null);
    m.put("lastPickId", lastPickId);
    Map<Long, Integer> order =
        teamRows.stream().collect(Collectors.toMap(Team::getId, Team::getDraftPosition));
    m.put(
        "idleRevision",
        draftRevision(
            pickRows.size(),
            lastPickId,
            league.isDraftOpen(),
            league.isDraftFinalized(),
            league.isSnakeDraft(),
            order));
    m.put("available", pokemon.findByDraftedFalseOrderByNameAsc());
    m.put("settings", settingsMap(league));
    return m;
  }

  /** Lightweight live status; avoids transferring the entire Pokémon pool on polls. */
  @GetMapping("/draft/status")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> draftStatus(HttpSession session) {
    auth.current(session);
    LeagueSettingsRepository.DraftFlags league = settingsRepo.findDraftFlagsById(1L).orElseThrow();
    DraftPickRepository.Status status = picks.status();
    long count = status.getPickCount();
    Long currentId = draft.currentTeamId(count, league.isSnakeDraft());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("pickCount", count);
    result.put("lastPickId", status.getLastPickId());
    result.put("currentTeamId", currentId);
    result.put("draftOpen", league.isDraftOpen());
    result.put("draftFinalized", league.isDraftFinalized());
    result.put("snakeDraft", league.isSnakeDraft());
    return result;
  }

  /**
   * A closed room only needs a tiny change detector to notice reopening/undo. Keep the same
   * five-second responsiveness without loading live draft state.
   */
  @GetMapping("/draft/idle-status")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, String> idleDraftStatus(HttpSession session) {
    auth.current(session);
    var status = settingsRepo.idleStatus().orElseThrow();
    Map<Long, Integer> order = new HashMap<>();
    // Order can only be edited before the first pick. Include it in the revision
    // so a coach's paused board also notices commissioner order edits.
    if (status.getPickCount() == 0)
      for (TeamRepository.DraftOrder team : teams.findAllProjectedByOrderByDraftPositionAsc())
        order.put(team.getId(), team.getDraftPosition());
    return Map.of(
        "revision",
        draftRevision(
            status.getPickCount(),
            status.getLastPickId(),
            status.isDraftOpen(),
            status.isDraftFinalized(),
            status.isSnakeDraft(),
            order));
  }

  private String draftRevision(
      long count,
      Long lastId,
      boolean open,
      boolean finalized,
      boolean snake,
      Map<Long, Integer> order) {
    String positions = count == 0 ? new TreeMap<>(order).toString() : "";
    return count + ":" + lastId + ":" + open + ":" + finalized + ":" + snake + ":" + positions;
  }

  public record PickRequest(Long pokemonId) {}

  @PostMapping("/draft/pick")
  public DraftPick pick(@RequestBody PickRequest r, HttpSession session) {
    AppUser u = auth.current(session);
    return draft.pick(u.getTeamId(), u.getRole() == Role.MANAGER, r.pokemonId());
  }

  @PostMapping("/manager/draft/undo")
  public void undo(HttpSession session) {
    auth.manager(session);
    draft.undoLast();
  }

  @PostMapping("/manager/draft/finalize")
  public Map<String, Object> finalizeDraft(HttpSession session) {
    auth.manager(session);
    draft.finalizeDraft();
    return Map.of("finalized", true, "picks", picks.count());
  }

  @GetMapping("/standings")
  public Map<String, Object> standings(HttpSession session) {
    auth.current(session);
    Map<String, List<Team>> divisions = schedule.standings();
    return Map.of(
        "A",
        divisions.get("A"),
        "B",
        divisions.get("B"),
        "seedModeEnabled",
        settings().isPlayoffSeedModeEnabled());
  }

  @GetMapping("/schedule")
  public List<MatchupDto> schedule(HttpSession session) {
    auth.current(session);
    Map<Long, String> names = teamNames();
    return matchups.findPublished(null, null).stream()
        .map(m -> LeagueMapper.matchup(m, names))
        .toList();
  }

  @GetMapping("/playoffs")
  public Map<String, Object> playoffs(HttpSession session) {
    auth.current(session);
    return schedule.playoffBracket();
  }

  // The season uses a published fixture list. Schedule regeneration and
  // arbitrary matchup editing are intentionally unavailable.

  public record ResultRequest(int homeScore, int awayScore) {}

  @PostMapping("/manager/results/{id}")
  public void result(@PathVariable Long id, @RequestBody ResultRequest r, HttpSession session) {
    auth.manager(session);
    schedule.setResult(id, r.homeScore(), r.awayScore());
  }

  @DeleteMapping("/manager/results/{id}")
  public void clearResult(@PathVariable Long id, HttpSession session) {
    auth.manager(session);
    schedule.clearResult(id);
  }

  @GetMapping("/lineup")
  public Map<String, Object> lineup(HttpSession session) {
    AppUser u = auth.current(session);
    if (u.getRole() == Role.MANAGER)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Commissioners do not have a team lineup. Sign in as a coach to submit one.");
    LeagueSettings s = settings();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("week", s.getCurrentWeek());
    out.put("lineupRequired", SeasonCalendar.week(s.getCurrentWeek()).lineupRequired());
    out.put("weekLabel", SeasonCalendar.week(s.getCurrentWeek()).label());
    out.put("deadline", s.getLineupDeadline());
    out.put("roster", rosterView(u.getTeamId()));
    out.put(
        "submission",
        submissions.findByTeamIdAndWeek(u.getTeamId(), s.getCurrentWeek()).orElse(null));
    return out;
  }

  public record LineupRequest(List<Long> pokemonIds) {}

  // Legacy endpoint must use the same strict, one-way lineup locking rules.
  @PostMapping("/lineup")
  public WeeklySubmission submitLineup(@RequestBody LineupRequest r, HttpSession session) {
    AppUser u = auth.current(session);
    if (u.getRole() == Role.MANAGER)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Commissioners cannot submit team lineups.");
    return lineupService.submit(u.getTeamId(), r.pokemonIds());
  }

  @GetMapping("/lineups/week/{week}")
  public List<Map<String, Object>> weekLineups(@PathVariable int week, HttpSession session) {
    AppUser u = auth.current(session);
    LeagueSettings s = settings();
    if (week < 1 || week > SeasonCalendar.FINAL_WEEK || !SeasonCalendar.week(week).lineupRequired())
      return List.of();
    boolean reveal =
        u.getRole() == Role.MANAGER
            || (s.getLineupDeadline() != null
                && LocalDateTime.now(java.time.ZoneId.of("America/New_York"))
                    .isAfter(s.getLineupDeadline()));
    if (!reveal)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Lineups stay hidden until the deadline");
    List<WeeklySubmission> rows = submissions.findByWeek(week);
    Set<Long> ids =
        rows.stream()
            .flatMap(row -> Arrays.stream(row.getPokemonIdsCsv().split(",")))
            .filter(x -> !x.isBlank())
            .map(Long::valueOf)
            .collect(Collectors.toSet());
    Map<Long, String> names = pokemonNames(ids);
    Map<Long, String> teamNames = teamNames();
    return rows.stream().map(row -> submissionView(row, names, teamNames)).toList();
  }

  public record BidRequest(Long wantedPokemonId, Long dropPokemonId, int amount) {}

  @PostMapping("/free-agency/bids")
  public FreeAgentBid bid(@RequestBody BidRequest r, HttpSession session) {
    AppUser u = auth.current(session);
    if (u.getRole() == Role.MANAGER)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Commissioners cannot submit FAAB bids. Sign in as a coach to make a bid.");
    return freeAgency.submitBid(u.getTeamId(), r.wantedPokemonId(), r.dropPokemonId(), r.amount());
  }

  @DeleteMapping("/free-agency/bids/{bidId}")
  public void withdrawBid(@PathVariable Long bidId, HttpSession session) {
    AppUser user = auth.current(session);
    if (user.getRole() != Role.COACH)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only coaches can withdraw their own FAAB bids.");
    freeAgency.cancelBid(user.getTeamId(), bidId);
  }

  @GetMapping("/free-agency/bids")
  public List<Map<String, Object>> bids(HttpSession session) {
    AppUser u = auth.current(session);
    List<FreeAgentBid> visible =
        u.getRole() == Role.MANAGER
            ? bids.findAll()
            : bids.findByTeamIdOrderByCreatedAtDesc(u.getTeamId());
    Set<Long> ids = new HashSet<>();
    for (FreeAgentBid bid : visible) {
      ids.add(bid.getWantedPokemonId());
      ids.add(bid.getDropPokemonId());
    }
    ids.remove(null);
    Map<Long, String> names = pokemonNames(ids);
    return visible.stream()
        .map(
            b -> {
              Map<String, Object> result = new LinkedHashMap<>();
              result.put("id", b.getId());
              result.put("teamId", b.getTeamId());
              result.put("wantedPokemonId", b.getWantedPokemonId());
              result.put("dropPokemonId", b.getDropPokemonId());
              result.put("wantedPokemonName", names.get(b.getWantedPokemonId()));
              result.put("dropPokemonName", names.get(b.getDropPokemonId()));
              result.put("amount", b.getAmount());
              result.put("status", b.getStatus());
              result.put("createdAt", b.getCreatedAt());
              return result;
            })
        .toList();
  }

  @PostMapping("/manager/free-agency/process")
  public Map<String, Object> process(HttpSession session) {
    auth.manager(session);
    return freeAgency.processBids();
  }

  @GetMapping("/transactions")
  public List<TransactionHistoryService.Entry> transactions(HttpSession session) {
    auth.current(session);
    return history.history();
  }

  @GetMapping("/settings")
  public Map<String, Object> settingsGet(HttpSession session) {
    auth.current(session);
    return settingsMap(settings());
  }

  public record SettingsUpdate(
      String leagueName,
      Integer currentWeek,
      Integer regularSeasonWeeks,
      Boolean draftOpen,
      Boolean snakeDraft,
      Long scheduleSeed,
      String lineupDeadline) {}

  @PutMapping("/manager/settings")
  public Map<String, Object> updateSettings(@RequestBody SettingsUpdate r, HttpSession session) {
    auth.manager(session);
    LeagueSettings s = settings();
    if (r.leagueName() != null) s.setLeagueName(r.leagueName());
    if (r.currentWeek() != null) {
      if (r.currentWeek() < 1 || r.currentWeek() > SeasonCalendar.FINAL_WEEK)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Choose a week between 1 and 16.");
      s.setCurrentWeek(r.currentWeek());
    }
    if (r.regularSeasonWeeks() != null
        && r.regularSeasonWeeks() != SeasonCalendar.REGULAR_SEASON_END)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Published schedule ends in Week 13");
    if (r.draftOpen() != null) s.setDraftOpen(r.draftOpen());
    if (r.snakeDraft() != null && r.snakeDraft() != s.isSnakeDraft()) {
      if (picks.count() > 0 || s.isDraftFinalized())
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Snake/linear draft format cannot change after the first pick.");
      s.setSnakeDraft(r.snakeDraft());
    }
    if (r.draftOpen() != null && r.draftOpen() && s.isDraftFinalized())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The official draft is finalized. It cannot be reopened.");
    // Random seeds cannot change the published season schedule.
    if (r.lineupDeadline() != null)
      s.setLineupDeadline(
          r.lineupDeadline().isBlank() ? null : LocalDateTime.parse(r.lineupDeadline()));
    settingsRepo.save(s);
    return settingsMap(s);
  }

  private LeagueSettings settings() {
    return settingsRepo.findById(1L).orElseGet(() -> settingsRepo.save(new LeagueSettings()));
  }

  private Map<String, Object> settingsMap(LeagueSettings s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("leagueName", s.getLeagueName());
    m.put("currentWeek", s.getCurrentWeek());
    m.put("regularSeasonWeeks", s.getRegularSeasonWeeks());
    m.put("draftOpen", s.isDraftOpen());
    m.put("draftFinalized", s.isDraftFinalized());
    m.put("snakeDraft", s.isSnakeDraft());
    m.put("scheduleSeed", s.getScheduleSeed());
    m.put("lineupDeadline", s.getLineupDeadline());
    m.put("playoffSeedModeEnabled", s.isPlayoffSeedModeEnabled());
    m.put("waiverProcessed", s.isWaiverProcessed());
    FreeAgencyService.WaiverStatus waiver = freeAgency.waiverStatus(s);
    m.put("waiverWindowOpen", waiver.open());
    m.put("waiverClosedReason", waiver.reason());
    m.put("waiverPeriodsProcessed", waiver.processed());
    return m;
  }

  private Map<Long, String> teamNames() {
    return teams.findAllProjectedBy().stream()
        .collect(Collectors.toMap(TeamRepository.Name::getId, TeamRepository.Name::getName));
  }

  private List<RosterPokemonDto> rosterView(Long teamId) {
    return rosterViews(roster.findByTeamId(teamId)).getOrDefault(teamId, List.of());
  }

  private Map<Long, List<RosterPokemonDto>> rosterViews(List<RosterEntry> entries) {
    Set<Long> ids = entries.stream().map(RosterEntry::getPokemonId).collect(Collectors.toSet());
    Map<Long, Pokemon> mons =
        ids.isEmpty()
            ? Map.of()
            : pokemon.findAllById(ids).stream().collect(Collectors.toMap(Pokemon::getId, x -> x));
    Map<Long, List<RosterPokemonDto>> grouped = new HashMap<>();
    for (RosterEntry e : entries)
      grouped
          .computeIfAbsent(e.getTeamId(), k -> new ArrayList<>())
          .add(LeagueMapper.rosterPokemon(e, mons.get(e.getPokemonId())));
    grouped.values().forEach(list -> list.sort(Comparator.comparing(RosterPokemonDto::name)));
    return grouped;
  }

  private Map<Long, String> pokemonNames(Collection<Long> ids) {
    return ids.isEmpty()
        ? Map.of()
        : pokemon.findNamesByIdIn(ids).stream()
            .collect(
                Collectors.toMap(PokemonRepository.Name::getId, PokemonRepository.Name::getName));
  }

  private List<DraftPickDto> pickViews(List<DraftPick> rows, Map<Long, String> tn) {
    Map<Long, String> pn =
        pokemonNames(rows.stream().map(DraftPick::getPokemonId).collect(Collectors.toSet()));
    return rows.stream().map(p -> LeagueMapper.draftPick(p, tn, pn)).toList();
  }

  private Map<String, Object> submissionView(
      WeeklySubmission s, Map<Long, String> pn, Map<Long, String> teamNames) {
    List<String> names =
        Arrays.stream(s.getPokemonIdsCsv().split(","))
            .filter(x -> !x.isBlank())
            .map(Long::valueOf)
            .map(id -> pn.getOrDefault(id, "Unknown"))
            .toList();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("teamId", s.getTeamId());
    m.put("teamName", teamNames.getOrDefault(s.getTeamId(), "Unknown"));
    m.put("week", s.getWeek());
    m.put("pokemon", names);
    m.put("submittedAt", s.getSubmittedAt());
    return m;
  }
}
