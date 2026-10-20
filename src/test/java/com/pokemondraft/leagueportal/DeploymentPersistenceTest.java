package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Re-running the startup initializer must not rewrite an established league. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:deploymenttest;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@Transactional
class DeploymentPersistenceTest {
  @Autowired CommandLineRunner initializer;
  @Autowired LeagueSettingsRepository settings;
  @Autowired TeamRepository teams;
  @Autowired PokemonRepository pokemon;
  @Autowired MatchupRepository matchups;
  @Autowired AppUserRepository users;

  @Test
  void codeRedeployKeepsCustomizedLeagueState() throws Exception {
    LeagueSettings league = settings.findById(1L).orElseThrow();
    league.setCurrentWeek(7);
    league.setWaiverProcessed(true);
    league.setDraftOpen(false);
    settings.saveAndFlush(league);

    Team team = teams.findAll().get(0);
    Long teamId = team.getId();
    team.setFaBudget(1);
    team.setName("Still My Team");
    teams.saveAndFlush(team);

    Pokemon mon = pokemon.findByName("Sneasler").orElseThrow();
    Long pokemonId = mon.getId();
    mon.setPrice(9);
    pokemon.saveAndFlush(mon);

    Matchup existing = matchups.findAll().get(0);
    Long gameId = existing.getId();
    existing.setHomeScore(3);
    existing.setAwayScore(1);
    existing.setPlayed(true);
    matchups.saveAndFlush(existing);

    AppUser user =
        users.findAll().stream().filter(x -> x.getRole() == Role.COACH).findFirst().orElseThrow();
    Long userId = user.getId();
    user.setUsername("my-custom-login");
    users.saveAndFlush(user);

    initializer.run();

    assertEquals(7, settings.findById(1L).orElseThrow().getCurrentWeek());
    assertTrue(settings.findById(1L).orElseThrow().isWaiverProcessed());
    assertFalse(settings.findById(1L).orElseThrow().isDraftOpen());
    assertEquals("Still My Team", teams.findById(teamId).orElseThrow().getName());
    assertEquals(1, teams.findById(teamId).orElseThrow().getFaBudget());
    assertEquals(9, pokemon.findById(pokemonId).orElseThrow().getPrice());
    assertEquals(3, matchups.findById(gameId).orElseThrow().getHomeScore());
    assertEquals("my-custom-login", users.findById(userId).orElseThrow().getUsername());
  }
}
