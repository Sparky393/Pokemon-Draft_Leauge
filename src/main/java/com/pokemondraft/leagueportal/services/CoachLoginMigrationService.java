package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.AppUser;
import com.pokemondraft.leagueportal.entity.Role;
import com.pokemondraft.leagueportal.entity.Team;
import com.pokemondraft.leagueportal.repositories.AppUserRepository;
import com.pokemondraft.leagueportal.repositories.TeamRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Align coach logins to team.coach_name using the account's existing team_id. Never modifies team
 * rows, team assignments, passwords or user IDs.
 *
 * <p>Two-phase usernames avoid unique-index failures if names are swapped between coach accounts in
 * a live database. Each run is idempotent.
 */
@Service
public class CoachLoginMigrationService {
  private static final Logger log = LoggerFactory.getLogger(CoachLoginMigrationService.class);

  private final AppUserRepository users;
  private final TeamRepository teams;

  public CoachLoginMigrationService(AppUserRepository users, TeamRepository teams) {
    this.users = users;
    this.teams = teams;
  }

  @Transactional
  public void reconcile() {
    Map<Long, String> coachByTeam = new HashMap<>();
    for (Team team : teams.findAllMetadata()) {
      if (team.getId() != null && team.getCoachName() != null && !team.getCoachName().isBlank())
        coachByTeam.put(team.getId(), team.getCoachName().trim());
    }

    List<AppUser> accounts = users.findAll();
    Map<String, AppUser> ownerOfUsername = accounts.stream()
        .collect(Collectors.toMap(AppUser::getUsername, user -> user));
    Map<Long, AppUser> byId = accounts.stream()
        .collect(Collectors.toMap(AppUser::getId, user -> user));
    Map<Long, String> desired = new LinkedHashMap<>();

    for (AppUser account : accounts) {
      if (account.getRole() != Role.COACH || account.getTeamId() == null)
        continue;
      String coachName = coachByTeam.get(account.getTeamId());
      if (coachName != null && !coachName.equals(account.getUsername()))
        desired.put(account.getId(), coachName);
    }

    // No two users may share one username. Preserve the current records if
    // coach_name values conflict, rather than fail application startup.
    Map<String, Long> firstOwner = new HashMap<>();
    for (Map.Entry<Long, String> entry : new ArrayList<>(desired.entrySet())) {
      Long previous = firstOwner.putIfAbsent(entry.getValue(), entry.getKey());
      if (previous != null) {
        log.warn("Skipping conflicting login {} for accounts {} and {}", entry.getValue(), previous,
            entry.getKey());
        desired.remove(previous);
        desired.remove(entry.getKey());
      }
    }

    // If an existing username belongs to an account that will not move, its
    // name cannot be reassigned. Propagate that constraint through rename chains.
    boolean changed;
    do {
      changed = false;
      for (Map.Entry<Long, String> entry : new ArrayList<>(desired.entrySet())) {
        AppUser owner = ownerOfUsername.get(entry.getValue());
        if (owner != null && !owner.getId().equals(entry.getKey())
            && !desired.containsKey(owner.getId())) {
          log.warn("Cannot change coach login {} to {}; reserved by account {}", entry.getKey(),
              entry.getValue(), owner.getId());
          desired.remove(entry.getKey());
          changed = true;
        }
      }
    } while (changed);

    // Release all changing login names before assigning the final coach names.
    for (Long id : desired.keySet()) {
      AppUser account = byId.get(id);
      String temporary = "__coach_login_sync_" + id;
      if (ownerOfUsername.containsKey(temporary) && !temporary.equals(account.getUsername())) {
        log.warn("Temporary coach username {} is already in use; skipping rename", temporary);
        // This is a configuration problem; transaction rollback leaves all
        // accounts untouched rather than risk partial updates.
        throw new IllegalStateException("Coach login temporary name conflict");
      }
      account.setUsername(temporary);
      users.saveAndFlush(account);
    }

    for (Map.Entry<Long, String> entry : desired.entrySet()) {
      AppUser account = byId.get(entry.getKey());
      account.setUsername(entry.getValue());
      users.saveAndFlush(account);
    }
    if (!desired.isEmpty())
      log.info("Synchronized {} coach account usernames to existing team.coach_name values",
          desired.size());
  }
}
