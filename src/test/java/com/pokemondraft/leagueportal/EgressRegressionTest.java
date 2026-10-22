package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.*;
import com.pokemondraft.leagueportal.services.AuthService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** SQL measurements use disposable H2 only. No external database or credentials. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:egress;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.pokemondraft.leagueportal.EgressRegressionTest$SqlCapture"
    })
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EgressRegressionTest {
  public static class SqlCapture implements StatementInspector {
    static final List<String> queries = new CopyOnWriteArrayList<>();

    public String inspect(String sql) {
      queries.add(sql);
      return sql;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired TeamRepository teams;
  @Autowired AppUserRepository users;
  @Autowired PokemonRepository pokemon;
  @Autowired RosterEntryRepository roster;
  @Autowired EntityManagerFactory emf;
  @Autowired EntityManager em;
  @Autowired DraftPickRepository picks;
  @Autowired LeagueSettingsRepository settings;
  @Autowired WeeklySubmissionRepository submissions;
  @Autowired MatchupRepository matchups;
  @Autowired FreeAgentBidRepository bids;
  @Autowired TradeOfferRepository trades;
  @Autowired TransactionRecordRepository transactions;
  @Autowired DraftService draft;
  @Autowired com.pokemondraft.leagueportal.controllers.ApiController api;
  @Autowired WeeklyLineupService lineups;
  @Autowired TransactionHistoryService history;
  @Autowired ScheduleService schedule;

  @Test
  @Transactional
  void notificationReadsStayNarrowAndDeadlineAlertsRemainFresh() throws Exception {
    bids.deleteAllInBatch();
    submissions.deleteAllInBatch();
    var won = new FreeAgentBid(1L, pokemon.findAllByOrderByNameAsc().getFirst().getId(), null, 1);
    won.setStatus(BidStatus.WON);
    bids.saveAndFlush(won);
    var league = settings.findById(1L).orElseThrow();
    league.setDraftOpen(false);
    league.setCurrentWeek(1);
    league.setLineupDeadline(LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(2));
    settings.saveAndFlush(league);
    MockHttpSession coach = session("coach1");
    em.clear();
    SqlCapture.queries.clear();
    mvc.perform(get("/api/notifications").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value("FAAB bid won!"));
    assertEquals(
        3,
        SqlCapture.queries.stream().filter(q -> q.startsWith("select ")).count(),
        "Far-away deadlines need only identity, settings, and a narrow won-notice query");
    league = settings.findById(1L).orElseThrow();
    league.setLineupDeadline(LocalDateTime.now(ZoneId.of("America/New_York")).plusHours(2));
    settings.saveAndFlush(league);
    mvc.perform(get("/api/notifications").session(coach))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].title")
                .value(org.hamcrest.Matchers.hasItem("Your Week 1 lineup is due soon")));
    submissions.saveAndFlush(new WeeklySubmission(1L, 1, "1,2,3,4,5,6"));
    mvc.perform(get("/api/notifications").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    won.setStatus(BidStatus.LOST);
    bids.saveAndFlush(won);
    mvc.perform(get("/api/notifications").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @Transactional
  void resultUpdatesExcludeLogosAndPreserveBrandingAndBudgets() {
    Team before = teams.findById(1L).orElseThrow();
    String logo = before.getLogoBase64();
    String name = before.getName();
    int budget = before.getFaBudget();
    Matchup game = matchups.findForTeamAndWeek(1L, 1).getFirst();
    em.flush();
    em.clear();
    SqlCapture.queries.clear();
    schedule.setResult(game.getId(), 2, 1);
    assertEquals(0, logoPayloadSelects(), "Scoring must not read any logo bytes");
    em.clear();
    Team scored = teams.findById(1L).orElseThrow();
    assertEquals(logo, scored.getLogoBase64());
    assertEquals(name, scored.getName());
    assertEquals(budget, scored.getFaBudget());
    assertEquals(game.getHomeTeamId().equals(1L) ? 1 : -1, scored.getDifferential());
    em.clear();
    SqlCapture.queries.clear();
    schedule.clearResult(game.getId());
    assertEquals(0, logoPayloadSelects());
    em.clear();
    assertEquals(0, teams.findById(1L).orElseThrow().getDifferential());
  }

  @Test
  @Transactional
  void logoCacheChecksVersionAndAuthenticationBeforeReturningBytes() throws Exception {
    MockHttpSession coach = session("coach1");
    byte[] first = {1, 2, 3}, second = {4, 5, 6};
    teams.updateLogo(1L, Base64.getEncoder().encodeToString(first), "image/png", 1001L);
    mvc.perform(get("/api/teams/1/logo").session(coach))
        .andExpect(status().isOk())
        .andExpect(content().bytes(first));
    SqlCapture.queries.clear();
    mvc.perform(get("/api/teams/1/logo").session(coach))
        .andExpect(status().isOk())
        .andExpect(content().bytes(first));
    assertEquals(0, logoPayloadSelects(), "Warm image request reads metadata only");
    // Simulate an upload handled by a different Render instance.
    teams.updateLogo(1L, Base64.getEncoder().encodeToString(second), "image/png", 1002L);
    mvc.perform(get("/api/teams/1/logo").session(coach))
        .andExpect(status().isOk())
        .andExpect(content().bytes(second));
    mvc.perform(get("/api/teams/1/logo")).andExpect(status().isUnauthorized());
    teams.updateLogo(1L, null, null, 1003L);
    mvc.perform(get("/api/teams/1/logo").session(coach)).andExpect(status().isNotFound());
  }

  @Test
  @Transactional
  void lightweightDashboardPreservesDefaultResponseAndSkipsUnusedRoster() throws Exception {
    MockHttpSession coach = session("coach1");
    mvc.perform(get("/api/dashboard").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myRoster").isArray());
    em.clear();
    SqlCapture.queries.clear();
    mvc.perform(get("/api/dashboard/view").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myRoster").doesNotExist())
        .andExpect(jsonPath("$.teams.length()").value(16))
        .andExpect(jsonPath("$.user.username").value("coach1"))
        .andExpect(jsonPath("$.settings").exists());
    assertTrue(SqlCapture.queries.stream().noneMatch(q -> q.contains("from roster_entry")));
    assertEquals(
        5,
        SqlCapture.queries.stream().filter(q -> q.startsWith("select ")).count(),
        "Home omits the two unused roster/Pokemon reads from the seven-query dashboard");
  }

  @Test
  @Transactional
  void largeHistoryCachesChunksAndImmediatelyReflectsAppendDeleteAndReset() {
    transactions.deleteAllInBatch();
    for (int i = 0; i < 5200; i++)
      transactions.save(new TransactionRecord("TEST_HISTORY", "History " + i));
    transactions.flush();
    assertEquals(5200, history.history().size());
    SqlCapture.queries.clear();
    assertEquals(5200, history.history().size());
    assertEquals(
        1,
        SqlCapture.queries.stream().filter(q -> q.startsWith("select ")).count(),
        "History over the previous 5000-row limit still uses a tiny warm revision");
    TransactionRecord appended =
        transactions.saveAndFlush(new TransactionRecord("TEST_HISTORY", "Appended"));
    SqlCapture.queries.clear();
    var fresh = history.history();
    assertEquals(5201, fresh.size());
    assertEquals(appended.getId(), fresh.getFirst().id());
    long payloadReads =
        SqlCapture.queries.stream()
            .filter(q -> q.startsWith("select ") && q.contains(".description"))
            .count();
    assertTrue(payloadReads <= 2, "Append reloads at most the changed/new ID chunks");
    transactions.deleteById(fresh.getLast().id());
    transactions.flush();
    assertEquals(5200, history.history().size());
    transactions.deleteAllInBatch();
    assertTrue(history.history().isEmpty());
  }

  @Test
  @Transactional
  void automaticLineupsBatchAllMissingTeamsAndKeepSelectionsStable() {
    submissions.deleteAllInBatch();
    var league = settings.findById(1L).orElseThrow();
    league.setCurrentWeek(2);
    league.setLineupDeadline(LocalDateTime.now(ZoneId.of("America/New_York")).minusMinutes(1));
    var owned = roster.findByTeamId(1L).stream().map(RosterEntry::getPokemonId).sorted().toList();
    List<Long> reversed = new ArrayList<>(owned);
    Collections.reverse(reversed);
    submissions.saveAndFlush(
        new WeeklySubmission(
            1L,
            1,
            reversed.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","))));
    settings.flush();
    em.clear();
    SqlCapture.queries.clear();
    lineups.finalizeExpired(2);
    long reads = SqlCapture.queries.stream().filter(q -> q.startsWith("select ")).count();
    assertTrue(reads <= 5, "All 16 automatic lineups use five batched reads");
    var rows = submissions.findByWeek(2);
    assertEquals(16, rows.size());
    assertTrue(rows.stream().allMatch(WeeklySubmission::isAutomatic));
    assertEquals(
        reversed.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")),
        submissions.findByTeamIdAndWeek(1L, 2).orElseThrow().getPokemonIdsCsv());
  }

  private MockHttpSession session(String username) {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(
        AuthService.SESSION_USER_ID, users.findByUsername(username).orElseThrow().getId());
    return session;
  }

  private long logoPayloadSelects() {
    return SqlCapture.queries.stream()
        .filter(q -> q.startsWith("select "))
        .filter(q -> q.matches("(?s).*\\.logo_base64(?:,|\\s+from).*"))
        .count();
  }

  @Test
  @Order(1)
  void measureRepresentativeReads() throws Exception {
    List<Pokemon> pool = pokemon.findAllByOrderByNameAsc();
    List<Team> all = teams.findAll();
    String logo = Base64.getEncoder().encodeToString(new byte[100 * 1024]);
    for (int i = 0; i < all.size(); i++) {
      Team team = all.get(i);
      team.setLogoBase64(logo);
      team.setLogoContentType("image/png");
      team.setLogoVersion(42L);
      teams.saveAndFlush(team);
      for (int j = 0; j < 6; j++)
        roster.save(new RosterEntry(team.getId(), pool.get(i * 6 + j).getId()));
    }
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(
        AuthService.SESSION_USER_ID, users.findByUsername("coach1").orElseThrow().getId());
    for (String endpoint :
        List.of(
            "/api/draft/status",
            "/api/draft",
            "/api/rosters",
            "/api/dashboard",
            "/api/playoffs",
            "/api/flow/schedule",
            "/api/settings",
            "/api/weeks/locks",
            "/api/exports/transactions")) {
      emf.getCache().evictAll();
      SqlCapture.queries.clear();
      mvc.perform(get(endpoint).session(session)).andExpect(status().isOk());
      long selects = SqlCapture.queries.stream().filter(q -> q.startsWith("select ")).count();
      long logos = logoPayloadSelects();
      assertEquals(0, logos, endpoint + " must not retrieve logo payloads");
      Map<String, Integer> limits =
          Map.of(
              "/api/draft/status",
              4,
              "/api/draft",
              6,
              "/api/rosters",
              4,
              "/api/dashboard",
              7,
              "/api/playoffs",
              3,
              "/api/flow/schedule",
              5,
              "/api/settings",
              3,
              "/api/weeks/locks",
              2,
              "/api/exports/transactions",
              2);
      assertTrue(selects <= limits.get(endpoint), endpoint + " SELECT regression: " + selects);
      System.out.printf("EGRESS %s selects=%d logoPayloadSelects=%d%n", endpoint, selects, logos);
    }
  }

  @Test
  @Transactional
  void metadataAndBrandingKeepImageBytesAndOtherTeamFields() throws Exception {
    MockHttpSession coach = session("coach1");
    Team original = teams.findById(1L).orElseThrow();
    String before = original.getLogoBase64();
    int budget = original.getFaBudget();
    em.flush();
    em.clear();
    SqlCapture.queries.clear();
    mvc.perform(get("/api/teams").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].logoUrl").value("/api/teams/1/logo?v=42"))
        .andExpect(jsonPath("$[0].logoBase64").doesNotExist());
    assertEquals(0, logoPayloadSelects());
    assertEquals(before, teams.findById(1L).orElseThrow().getLogoBase64());
    byte[] png =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9YueWUkAAAAASUVORK5CYII=");
    em.clear();
    SqlCapture.queries.clear();
    mvc.perform(
            multipart("/api/teams/me/logo")
                .file(new MockMultipartFile("file", "logo.png", "image/png", png))
                .session(coach)
                .header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logoUrl").isString());
    assertEquals(0, logoPayloadSelects(), "Replacing a logo must not retrieve the previous image");
    em.clear();
    mvc.perform(get("/api/teams/1/logo").session(coach))
        .andExpect(status().isOk())
        .andExpect(content().bytes(png))
        .andExpect(content().contentType("image/png"))
        .andExpect(
            header()
                .string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=604800")));
    mvc.perform(
            put("/api/teams/me/name")
                .session(coach)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Still My Logo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Still My Logo"));
    em.clear();
    Team renamed = teams.findById(1L).orElseThrow();
    assertEquals(Base64.getEncoder().encodeToString(png), renamed.getLogoBase64());
    assertEquals(budget, renamed.getFaBudget());
    mvc.perform(
            delete("/api/teams/me/logo")
                .session(coach)
                .header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logoUrl").doesNotExist());
    em.clear();
    mvc.perform(get("/api/teams/1/logo").session(coach)).andExpect(status().isNotFound());
    mvc.perform(
            post("/api/draft/pick")
                .session(coach)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @Transactional
  void entireSnakeDraftPreservesOrderBudgetsAndFinalization() throws Exception {
    roster.deleteAllInBatch();
    picks.deleteAllInBatch();
    List<Pokemon> pool = pokemon.findAllByOrderByNameAsc();
    for (Pokemon p : pool) {
      p.setDrafted(false);
      p.setPrice(5);
    }
    pokemon.saveAllAndFlush(pool);
    LeagueSettings league = settings.findById(1L).orElseThrow();
    league.setDraftOpen(true);
    league.setDraftFinalized(false);
    league.setSnakeDraft(true);
    settings.saveAndFlush(league);
    List<Team> ordered =
        teams.findAllMetadata().stream()
            .sorted(Comparator.comparingInt(Team::getDraftPosition))
            .toList();
    for (int i = 0; i < 160; i++) {
      int slot = i / 16 % 2 == 0 ? i % 16 : 15 - i % 16;
      Long teamId = ordered.get(slot).getId();
      assertEquals(teamId, draft.currentTeamId(i, league));
      DraftPick pick = draft.pick(teamId, false, pool.get(i).getId());
      assertEquals(i + 1, pick.getOverallPick());
      assertEquals(teamId, pick.getTeamId());
    }
    assertEquals(160, picks.count());
    assertNull(draft.currentTeam());
    for (Team team : ordered) {
      assertEquals(10, roster.countByTeamId(team.getId()));
      assertEquals(50, roster.totalPriceByTeamId(team.getId()));
      assertEquals(team.getFaBudget(), teams.findById(team.getId()).orElseThrow().getFaBudget());
    }
    mvc.perform(get("/api/rosters").session(session("coach1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].pokemon.length()").value(10));
    draft.finalizeDraft();
    mvc.perform(get("/api/draft/status").session(session("coach1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pickCount").value(160))
        .andExpect(jsonPath("$.draftFinalized").value(true))
        .andExpect(jsonPath("$.draftOpen").value(false));
    assertThrows(org.springframework.web.server.ResponseStatusException.class, draft::undoLast);
  }

  @Test
  @Transactional
  void filteredSchedulePreservesHiddenAndRevealedLineups() throws Exception {
    Matchup game = matchups.findByWeekOrderByIdAsc(1).get(0);
    long home = game.getHomeTeamId(), away = game.getAwayTeamId();
    List<Long> homeIds = roster.findByTeamId(home).stream().map(RosterEntry::getPokemonId).toList();
    List<Long> awayIds = roster.findByTeamId(away).stream().map(RosterEntry::getPokemonId).toList();
    LeagueSettings league = settings.findById(1L).orElseThrow();
    league.setLineupDeadline(LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(1));
    settings.saveAndFlush(league);
    lineups.submit(home, homeIds);
    MockHttpSession coach = session("coach1");
    mvc.perform(
            get("/api/flow/schedule")
                .param("week", "1")
                .param("teamId", String.valueOf(home))
                .session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].homeSubmitted").value(true))
        .andExpect(jsonPath("$[0].awaySubmitted").value(false))
        .andExpect(jsonPath("$[0].revealed").value(false))
        .andExpect(jsonPath("$[0].homeLineup").doesNotExist());
    lineups.submit(away, awayIds);
    submissions.saveAndFlush(new WeeklySubmission(home, 2, "1,2,3,4,5,6"));
    assertEquals(
        2,
        submissions.findForMatchups(List.of(game.getId())).size(),
        "Scoped batch excludes other weeks");
    mvc.perform(
            get("/api/flow/schedule")
                .param("week", "1")
                .param("teamId", String.valueOf(home))
                .session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].revealed").value(true))
        .andExpect(jsonPath("$[0].homeLineup.length()").value(6))
        .andExpect(jsonPath("$[0].awayLineup.length()").value(6));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> lineups.submit(home, homeIds));
    mvc.perform(get("/api/lineups/week/1").session(coach)).andExpect(status().isForbidden());
    mvc.perform(get("/api/lineups/week/1").session(session("manager")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @Transactional
  void faabProcessingImmediatelyUpdatesBidsRosterNotificationsAndCachedHistory() throws Exception {
    roster.deleteAllInBatch();
    bids.deleteAllInBatch();
    transactions.deleteAllInBatch();
    List<Pokemon> pool = pokemon.findAllByOrderByNameAsc();
    for (int i = 0; i < 10; i++) {
      Pokemon p = pool.get(i);
      p.setPrice(5);
      p.setDrafted(true);
      pokemon.save(p);
      roster.save(new RosterEntry(1L, p.getId()));
    }
    Pokemon wanted = pool.get(20);
    wanted.setPrice(4);
    wanted.setDrafted(false);
    pokemon.saveAndFlush(wanted);
    LeagueSettings league = settings.findById(1L).orElseThrow();
    league.setCurrentWeek(4);
    settings.saveAndFlush(league);
    for (Matchup game : matchups.findByWeekOrderByIdAsc(3)) {
      game.setPlayed(true);
      game.setHomeScore(2);
      game.setAwayScore(1);
      matchups.save(game);
    }
    FreeAgencyService bidding = faabAt(11, 26, 12);
    FreeAgentBid bid = bidding.submitBid(1L, wanted.getId(), pool.get(0).getId(), 1);
    bids.flush();
    MockHttpSession coach = session("coach1");
    mvc.perform(get("/api/free-agency/bids").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("PENDING"));
    mvc.perform(get("/api/free-agency/bids").session(session("coach2")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    assertTrue(history.history().isEmpty());
    SqlCapture.queries.clear();
    assertTrue(history.history().isEmpty());
    assertEquals(
        1,
        SqlCapture.queries.stream().filter(q -> q.startsWith("select ")).count(),
        "Warm history only reads its revision");
    assertEquals(1, faabAt(11, 28, 19).processBids().get("winners"));
    em.flush();
    em.clear();
    assertEquals(BidStatus.WON, bids.findById(bid.getId()).orElseThrow().getStatus());
    assertEquals(2, teams.findById(1L).orElseThrow().getFaBudget());
    assertEquals(10, roster.countByTeamId(1L));
    assertTrue(roster.findByPokemonId(wanted.getId()).isPresent());
    assertTrue(roster.findByPokemonId(pool.get(0).getId()).isEmpty());
    assertEquals(2, history.history().size(), "New journal rows invalidate the cache immediately");
    mvc.perform(get("/api/free-agency/bids").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("WON"))
        .andExpect(jsonPath("$[0].wantedPokemonName").value(wanted.getName()));
    mvc.perform(get("/api/notifications").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.hasItem("FAAB bid won!")));
    transactions.deleteAllInBatch();
    assertTrue(history.history().isEmpty(), "Reset invalidates cached journal rows");
  }

  private FreeAgencyService faabAt(int month, int day, int hour) {
    ZoneId eastern = ZoneId.of("America/New_York");
    Clock clock =
        Clock.fixed(
            LocalDateTime.of(2026, month, day, hour, 0).atZone(eastern).toInstant(), eastern);
    return new FreeAgencyService(
        bids, teams, pokemon, roster, transactions, trades, matchups, settings, clock);
  }

  @Test
  @Transactional
  void draftStatusHandlesLegacyNullFinalizedFlagAndHasNoPasswordsOrImages() throws Exception {
    em.createNativeQuery("update league_settings set draft_finalized = null where id = 1")
        .executeUpdate();
    MockHttpSession coach = session("coach1");
    em.clear();
    SqlCapture.queries.clear();
    mvc.perform(get("/api/draft/status").session(coach))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draftFinalized").value(false));
    assertEquals(0, logoPayloadSelects());
    assertTrue(
        SqlCapture.queries.stream()
            .noneMatch(q -> q.startsWith("select ") && q.contains(".password")),
        "Ordinary authorization must not fetch password hashes");
  }

  @Test
  @Transactional
  void idleRevisionDetectsReopeningAndUndoRepickWithoutLoadingTeams() throws Exception {
    roster.deleteAllInBatch();
    picks.deleteAllInBatch();
    MockHttpSession coach = session("coach1");
    LeagueSettings league = settings.findById(1L).orElseThrow();
    league.setDraftOpen(true);
    settings.saveAndFlush(league);
    List<Pokemon> pool = pokemon.findAllByOrderByNameAsc();
    draft.pick(1L, true, pool.get(0).getId());
    em.flush();
    String first = (String) api.draftState(coach).get("idleRevision");
    SqlCapture.queries.clear();
    assertEquals(first, api.idleDraftStatus(coach).get("revision"));
    assertEquals(2, SqlCapture.queries.stream().filter(q -> q.startsWith("select ")).count());
    assertEquals(0, logoPayloadSelects());
    draft.undoLast();
    draft.pick(1L, true, pool.get(1).getId());
    em.flush();
    assertNotEquals(
        first,
        api.idleDraftStatus(coach).get("revision"),
        "Undo and repick at the same count must refresh other tabs");
    String open = api.idleDraftStatus(coach).get("revision");
    league.setDraftOpen(false);
    settings.saveAndFlush(league);
    String closed = api.idleDraftStatus(coach).get("revision");
    assertNotEquals(open, closed);
    assertEquals(closed, api.draftState(coach).get("idleRevision"));
  }

  @Test
  @Transactional
  void batchedTradeValuationPreservesOwnershipBudgetsAndConflictingOfferCancellation() {
    roster.deleteAllInBatch();
    trades.deleteAllInBatch();
    transactions.deleteAllInBatch();
    List<Pokemon> pool = pokemon.findAllByOrderByNameAsc();
    for (int i = 0; i < 20; i++) {
      Pokemon mon = pool.get(i);
      mon.setPrice(5);
      mon.setDrafted(true);
      pokemon.save(mon);
      roster.save(new RosterEntry(i < 10 ? 1L : 2L, mon.getId()));
    }
    em.flush();
    FreeAgencyService service = faabAt(11, 26, 12);
    TradeOffer accepted =
        service.offerTrade(
            1L, 2L, List.of(pool.get(0).getId()), List.of(pool.get(10).getId()), 1, 0);
    TradeOffer conflicting =
        service.offerTrade(
            1L, 2L, List.of(pool.get(0).getId()), List.of(pool.get(10).getId()), 0, 0);
    TradeOffer unrelated =
        service.offerTrade(
            1L, 2L, List.of(pool.get(1).getId()), List.of(pool.get(11).getId()), 0, 0);
    service.respondTrade(accepted.getId(), 2L, false, true);
    em.flush();
    em.clear();
    assertEquals(TradeStatus.ACCEPTED, trades.findById(accepted.getId()).orElseThrow().getStatus());
    assertEquals(
        TradeStatus.CANCELLED, trades.findById(conflicting.getId()).orElseThrow().getStatus());
    assertEquals(TradeStatus.PENDING, trades.findById(unrelated.getId()).orElseThrow().getStatus());
    assertEquals(2L, roster.findByPokemonId(pool.get(0).getId()).orElseThrow().getTeamId());
    assertEquals(1L, roster.findByPokemonId(pool.get(10).getId()).orElseThrow().getTeamId());
    assertEquals(10, roster.countByTeamId(1L));
    assertEquals(10, roster.countByTeamId(2L));
    assertEquals(50, roster.totalPriceByTeamId(1L));
    assertEquals(50, roster.totalPriceByTeamId(2L));
    assertEquals(2, teams.findById(1L).orElseThrow().getFaBudget());
    assertEquals(4, teams.findById(2L).orElseThrow().getFaBudget());
    assertTrue(
        transactions
            .findAllByOrderByCreatedAtDesc()
            .get(0)
            .getDescription()
            .contains(pool.get(0).getName()));
  }
}
