package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.AuthService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Disposable H2 proves completed picks expose actual prices in the existing batched read. */
@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:draftprice;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.pokemondraft.leagueportal.DraftPriceViewTest$SqlCapture" })
@AutoConfigureMockMvc
@Transactional
class DraftPriceViewTest {
  public static class SqlCapture implements StatementInspector {
    static final List<String> queries = new CopyOnWriteArrayList<>();

    public String inspect(String sql) {
      queries.add(sql);
      return sql;
    }
  }

  @Autowired
  MockMvc mvc;
  @Autowired
  PokemonRepository pokemon;
  @Autowired
  DraftPickRepository picks;
  @Autowired
  RosterEntryRepository roster;
  @Autowired
  jakarta.persistence.EntityManager em;

  @Test
  void completedPickPriceIsCorrectAndZeroIsDistinctFromAnUnknownPrice() throws Exception {
    Pokemon priced = pokemon.findByName("Garchomp").orElseThrow();
    priced.setPrice(18);
    priced.setDrafted(true);
    Pokemon free = pokemon.findByName("Charizard").orElseThrow();
    free.setPrice(0);
    free.setDrafted(true);
    pokemon.saveAll(List.of(priced, free));
    picks.save(new DraftPick(1, 1, 1L, priced.getId()));
    picks.save(new DraftPick(2, 1, 2L, free.getId()));
    // An imported/orphaned pick must not pretend the missing Pokémon cost $0.
    picks.save(new DraftPick(3, 1, 3L, Long.MAX_VALUE));
    roster.save(new RosterEntry(1L, priced.getId()));
    roster.save(new RosterEntry(2L, free.getId()));
    em.flush();
    em.clear();
    MockHttpSession guest = new MockHttpSession();
    guest.setAttribute(AuthService.SESSION_GUEST, Boolean.TRUE);
    SqlCapture.queries.clear();
    mvc.perform(get("/api/draft").session(guest)).andExpect(status().isOk())
        .andExpect(jsonPath("$.picks[0].pokemonName").value("Garchomp"))
        .andExpect(jsonPath("$.picks[0].price").value(18))
        .andExpect(jsonPath("$.picks[1].price").value(0))
        .andExpect(jsonPath("$.picks[2].price").isEmpty());
    assertEquals(1, SqlCapture.queries.stream()
        .filter(q -> q.startsWith("select ") && q.contains("price") && q.contains(" in ")).count(),
        "All completed picks should share one narrow price/name query");
    assertTrue(SqlCapture.queries.stream().filter(q -> q.startsWith("select ")).count() <= 6,
        "Guest draft reads should not add an extra query per pick");
    assertTrue(SqlCapture.queries.stream().allMatch(q -> q.startsWith("select ")),
        "Viewing draft prices must never write league data");
  }
}
