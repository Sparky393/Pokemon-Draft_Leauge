package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:neonlogintest;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop" })
@Transactional
class NeonCoachLoginMigrationTest {
  @Autowired
  AppUserRepository users;
  @Autowired
  TeamRepository teams;
  @Autowired
  CoachLoginMigrationService migration;
  @Autowired
  ScheduleService schedule;
  @Autowired
  MatchupRepository matchups;

  private AppUser userFor(long teamId) {
    return users.findAll().stream().filter(u -> Objects.equals(u.getTeamId(), teamId)).findFirst()
        .orElseThrow();
  }

  @Test
  void fixesSwappedUsernamesUsingTeamIdsWithoutTouchingTeamRecordsOrPasswords() {
    Team first = teams.findById(1L).orElseThrow();
    Team second = teams.findById(2L).orElseThrow();
    first.setCoachName("Wide Right");
    second.setCoachName("Burn");
    teams.saveAndFlush(first);
    teams.saveAndFlush(second);

    AppUser u1 = userFor(1), u2 = userFor(2);
    Long u1Id = u1.getId(), u2Id = u2.getId();
    String originalPassword1 = u1.getPassword();
    String originalPassword2 = u2.getPassword();
    String team1Name = first.getName(), team2Name = second.getName();
    int originalDraft1 = first.getDraftPosition(), originalDraft2 = second.getDraftPosition();

    // As can happen in Neon, user logins belong to the opposite coaches.
    u1.setUsername("Burn");
    u2.setUsername("Wide Right");
    users.saveAndFlush(u1);
    users.saveAndFlush(u2);

    assertDoesNotThrow(migration::reconcile);
    assertEquals("Wide Right", users.findById(u1Id).orElseThrow().getUsername());
    assertEquals("Burn", users.findById(u2Id).orElseThrow().getUsername());
    assertEquals(1L, users.findById(u1Id).orElseThrow().getTeamId());
    assertEquals(2L, users.findById(u2Id).orElseThrow().getTeamId());
    assertEquals(originalPassword1, users.findById(u1Id).orElseThrow().getPassword());
    assertEquals(originalPassword2, users.findById(u2Id).orElseThrow().getPassword());

    assertEquals(team1Name, teams.findById(1L).orElseThrow().getName());
    assertEquals(team2Name, teams.findById(2L).orElseThrow().getName());
    assertEquals(originalDraft1, teams.findById(1L).orElseThrow().getDraftPosition());
    assertEquals(originalDraft2, teams.findById(2L).orElseThrow().getDraftPosition());
    assertDoesNotThrow(migration::reconcile, "Repeated Render starts must be safe");
    assertEquals("manager", users.findAll().stream().filter(u -> u.getRole() == Role.MANAGER)
        .findFirst().orElseThrow().getUsername());
  }

  @Test
  void duplicateDraftPositionsDoNotPreventPublishedMatchupMigration() {
    List<Team> current = teams.findAll();
    assertEquals(16, current.size());
    Map<Long, Integer> originalPositions = new HashMap<>();
    for (Team t : current) {
      int slot = t.getDraftPosition();
      t.setName(SeasonCalendar.teamNameForSlot(slot));
      t.setCoachName(SeasonCalendar.teamNameForSlot(slot));
      if (slot == 15)
        t.setDraftPosition(14); // duplicate in production screenshot
      if (slot == 16)
        t.setDraftPosition(14); // another duplicate
      teams.saveAndFlush(t);
      originalPositions.put(t.getId(), t.getDraftPosition());
    }

    assertDoesNotThrow(schedule::syncOfficialSchedule);
    assertEquals(80,
        matchups.findAll().stream().filter(m -> m.getWeek() >= 1 && m.getWeek() <= 13).count());
    for (Team t : teams.findAll()) {
      assertEquals(originalPositions.get(t.getId()).intValue(), t.getDraftPosition());
      assertNotNull(t.getName());
    }
  }
}
