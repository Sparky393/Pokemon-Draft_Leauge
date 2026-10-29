package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:repositorytest;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@Transactional
class RepositoryIntegrationTest {
  @Autowired AppUserRepository users;
  @Autowired FreeAgentBidRepository bids;
  @Autowired LeagueSettingsRepository settings;

  @Test
  void savesUserAndRetrievesByUsername() {
    AppUser user = users.save(new AppUser("repository-test-coach", "hashed-value", Role.COACH, 1L));
    AppUser found = users.findByUsername("repository-test-coach").orElseThrow();
    assertEquals(user.getId(), found.getId());
    assertEquals(Role.COACH, found.getRole());
  }

  @Test
  void persistsPendingBidAndWaiverProcessedFlag() {
    FreeAgentBid bid = bids.save(new FreeAgentBid(1L, 99L, 10L, 3));
    assertTrue(
        bids.findByStatus(BidStatus.PENDING).stream().anyMatch(b -> b.getId().equals(bid.getId())));
    LeagueSettings s = settings.findById(1L).orElseThrow();
    s.setWaiverProcessed(true);
    settings.saveAndFlush(s);
    assertTrue(settings.findById(1L).orElseThrow().isWaiverProcessed());
  }
}
