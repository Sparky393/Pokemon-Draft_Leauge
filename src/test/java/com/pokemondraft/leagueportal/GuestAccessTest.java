package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.AuthService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Disposable H2 only; SQL capture checks guest reads as well as denied writes. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:guesttest;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.pokemondraft.leagueportal.GuestAccessTest$SqlCapture"
    })
@AutoConfigureMockMvc
@Transactional
class GuestAccessTest {
  public static class SqlCapture implements StatementInspector {
    static final List<String> sql = new CopyOnWriteArrayList<>();

    public String inspect(String query) {
      sql.add(query);
      return query;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired RequestMappingHandlerMapping mappings;
  @Autowired AppUserRepository users;
  @Autowired TeamRepository teams;
  @Autowired PokemonRepository pokemon;
  @Autowired RosterEntryRepository roster;
  @Autowired MatchupRepository matchups;
  @Autowired LeagueSettingsRepository settings;
  @Autowired WeeklySubmissionRepository submissions;
  @Autowired FreeAgentBidRepository bids;
  @Autowired EntityManager em;

  private MockHttpSession guest() throws Exception {
    MockHttpSession session = new MockHttpSession();
    mvc.perform(
            post("/api/auth/guest").session(session).header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("GUEST"))
        .andExpect(jsonPath("$.id").isEmpty())
        .andExpect(jsonPath("$.teamId").isEmpty());
    return session;
  }

  @Test
  void enteringGuestCreatesNoAccountOrTeamAndSurvivesRefresh() throws Exception {
    long accounts = users.count(), clubs = teams.count();
    SqlCapture.sql.clear();
    MockHttpSession session = guest();
    assertTrue(SqlCapture.sql.isEmpty(), "Entering guest mode must not query the database");
    assertNull(session.getAttribute(AuthService.SESSION_USER_ID));
    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("GUEST"));
    assertEquals(accounts, users.count());
    assertEquals(clubs, teams.count());
  }

  @Test
  void logoutInvalidatesTheSessionWithoutReadingOrWritingTheDatabase() throws Exception {
    MockHttpSession session = guest();
    SqlCapture.sql.clear();
    mvc.perform(
            post("/api/auth/logout").session(session).header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isOk());
    assertTrue(session.isInvalid());
    assertTrue(SqlCapture.sql.isEmpty(), "Logout must only invalidate the HTTP session");
  }

  @Test
  void everyMappedLeagueMutationIsDeniedBeforeDatabaseAccess() throws Exception {
    MockHttpSession session = guest();
    Set<String> sessionOnly = Set.of("/api/auth/login", "/api/auth/logout", "/api/auth/guest");
    int checked = 0;
    for (var mapping : mappings.getHandlerMethods().keySet()) {
      for (String pattern : mapping.getPatternValues()) {
        if (!pattern.startsWith("/api/") || sessionOnly.contains(pattern)) continue;
        for (RequestMethod method : mapping.getMethodsCondition().getMethods()) {
          if (Set.of(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS).contains(method))
            continue;
          String path = pattern.replaceAll("\\{[^}]+}", "1");
          SqlCapture.sql.clear();
          var action =
              method == RequestMethod.POST && pattern.endsWith("/logo")
                  ? multipart(path).file("file", new byte[] {1})
                  : request(HttpMethod.valueOf(method.name()), path)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}");
          mvc.perform(action.session(session).header("X-Requested-With", "XMLHttpRequest"))
              .andExpect(status().isForbidden());
          assertTrue(SqlCapture.sql.isEmpty(), method + " " + path + " queried the database");
          checked++;
        }
      }
    }
    assertTrue(checked >= 15, "The mutation inventory should include coach and manager operations");
  }

  @Test
  void privateAndUnreviewedGetsAreDeniedWithoutQueries() throws Exception {
    MockHttpSession session = guest();
    for (String path :
        List.of(
            "/api/lineup",
            "/api/flow/lineup",
            "/api/lineups/week/1",
            "/api/free-agency/bids",
            "/api/auth/security",
            "/api/auth/manager/users",
            "/api/manager/reminders",
            "/api/manager/league-reset/preview",
            "/api/notifications",
            "/api/exports/rosters",
            "/api/future-private-endpoint")) {
      SqlCapture.sql.clear();
      mvc.perform(get(path).session(session)).andExpect(status().isForbidden());
      assertTrue(SqlCapture.sql.isEmpty(), path + " queried private data");
    }
  }

  @Test
  void allPublicPagesAreReadableWithoutAnySqlWrites() throws Exception {
    MockHttpSession session = guest();
    Matchup first = matchups.findPublished(1, null).getFirst();
    List<Pokemon> mons = pokemon.findAllByOrderByNameAsc();
    for (int i = 0; i < 12; i++)
      roster.save(
          new RosterEntry(
              i < 6 ? first.getHomeTeamId() : first.getAwayTeamId(), mons.get(i).getId()));
    LeagueSettings league = settings.findById(1L).orElseThrow();
    league.setLineupDeadline(LocalDateTime.now(ZoneId.of("America/New_York")).minusDays(1));
    settings.save(league);
    em.flush();
    em.clear();
    for (String path :
        List.of(
            "/api/dashboard",
            "/api/dashboard/view",
            "/api/teams",
            "/api/pokemon",
            "/api/pokemon/free-agents",
            "/api/rosters",
            "/api/rosters/1",
            "/api/draft",
            "/api/draft/status",
            "/api/draft/idle-status",
            "/api/standings",
            "/api/schedule",
            "/api/playoffs",
            "/api/settings",
            "/api/flow/calendar",
            "/api/flow/schedule",
            "/api/weeks/locks",
            "/api/transactions")) {
      SqlCapture.sql.clear();
      mvc.perform(get(path).session(session)).andExpect(status().isOk());
      em.flush();
      assertTrue(
          SqlCapture.sql.stream()
              .allMatch(q -> q.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")),
          path + " attempted SQL writes: " + SqlCapture.sql);
    }
  }

  @Test
  void guestBracketDoesNotCreateFixturesAfterRegularSeasonFinishes() throws Exception {
    MockHttpSession session = guest();
    for (Matchup game : matchups.findPublished(null, null)) {
      game.setPlayed(true);
      game.setHomeScore(3);
      game.setAwayScore(0);
      matchups.save(game);
    }
    em.flush();
    em.clear();
    long fixtures = matchups.count();
    SqlCapture.sql.clear();
    mvc.perform(get("/api/playoffs").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.regularSeasonComplete").value(true));
    em.flush();
    assertTrue(
        SqlCapture.sql.stream()
            .allMatch(q -> q.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")));
    assertEquals(fixtures, matchups.count());
  }

  @Test
  void scheduleHidesOneSidedLineupsAndRevealsOnlyWhenBothSubmit() throws Exception {
    MockHttpSession session = guest();
    Matchup game = matchups.findPublished(1, null).getFirst();
    Pokemon mon = pokemon.findAllByOrderByNameAsc().getFirst();
    submissions.save(new WeeklySubmission(game.getHomeTeamId(), 1, mon.getId().toString()));
    bids.save(new FreeAgentBid(game.getHomeTeamId(), mon.getId(), null, 3));
    em.flush();
    mvc.perform(
            get("/api/flow/schedule")
                .param("week", "1")
                .param("teamId", game.getHomeTeamId().toString())
                .session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].revealed").value(false))
        .andExpect(jsonPath("$[0].homeLineup").doesNotExist())
        .andExpect(jsonPath("$[0].awayLineup").doesNotExist());
    mvc.perform(get("/api/free-agency/bids").session(session)).andExpect(status().isForbidden());
    submissions.save(new WeeklySubmission(game.getAwayTeamId(), 1, mon.getId().toString()));
    em.flush();
    mvc.perform(
            get("/api/flow/schedule")
                .param("week", "1")
                .param("teamId", game.getHomeTeamId().toString())
                .session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].revealed").value(true))
        .andExpect(jsonPath("$[0].homeLineup[0].pokemonId").value(mon.getId()))
        .andExpect(jsonPath("$[0].awayLineup[0].pokemonId").value(mon.getId()));
  }

  private void login(MockHttpSession session, String name, String password, String role)
      throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value(role));
    assertNull(session.getAttribute(AuthService.SESSION_GUEST));
  }

  @Test
  void guestCanLoginAsCoachOrManagerWithExistingPermissions() throws Exception {
    MockHttpSession coach = guest();
    login(coach, "coach1", "password", "COACH");
    mvc.perform(get("/api/flow/lineup").session(coach)).andExpect(status().isOk());
    mvc.perform(get("/api/free-agency/bids").session(coach)).andExpect(status().isOk());
    mvc.perform(get("/api/auth/manager/users").session(coach)).andExpect(status().isForbidden());
    MockHttpSession manager = guest();
    login(manager, "manager", "manager", "MANAGER");
    mvc.perform(get("/api/auth/manager/users").session(manager)).andExpect(status().isOk());
    mvc.perform(get("/api/manager/reminders").session(manager)).andExpect(status().isOk());
    // Re-entering guest mode must remove the previous account's privileges.
    mvc.perform(
            post("/api/auth/guest").session(manager).header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isOk());
    assertNull(manager.getAttribute(AuthService.SESSION_USER_ID));
    mvc.perform(get("/api/auth/manager/users").session(manager)).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/auth/logout").session(coach).header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }
}
