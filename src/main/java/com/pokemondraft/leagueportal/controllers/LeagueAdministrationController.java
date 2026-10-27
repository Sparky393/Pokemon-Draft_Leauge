package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.services.AuthService;
import com.pokemondraft.leagueportal.services.TestRosterService;
import com.pokemondraft.leagueportal.services.WeekLockService;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Management operations never execute automatically during deployment. */
@RestController
public class LeagueAdministrationController {
  @Value("${LEAGUE_RESET_PASSWORD:}")
  private String resetPassword;

  private final AuthService auth;
  private final TestRosterService resets;
  private final WeekLockService weekLocks;

  public LeagueAdministrationController(
      AuthService auth, TestRosterService resets, WeekLockService weekLocks) {
    this.auth = auth;
    this.resets = resets;
    this.weekLocks = weekLocks;
  }

  @GetMapping("/api/weeks/locks")
  public Map<Integer, Boolean> weekLocks(HttpSession session) {
    auth.viewer(session);
    return weekLocks.statuses();
  }

  public record WeekLockRequest(boolean locked) {}

  @PutMapping("/api/manager/weeks/{week}/lock")
  public Map<Integer, Boolean> lockWeek(
      @PathVariable int week, @RequestBody WeekLockRequest request, HttpSession session) {
    auth.manager(session);
    if (request == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing lock status");
    weekLocks.setLocked(week, request.locked());
    return weekLocks.statuses();
  }

  @GetMapping("/api/manager/league-reset/preview")
  public TestRosterService.ResetPreview resetPreview(HttpSession session) {
    auth.manager(session);
    return resets.resetPreview();
  }

  public record ResetRequest(String password) {}

  @PostMapping("/api/manager/league-reset")
  public Map<String, Integer> reset(@RequestBody ResetRequest request, HttpSession session) {
    auth.manager(session);
    if (resetPassword == null
        || resetPassword.isBlank()
        || request == null
        || request.password() == null
        || !MessageDigest.isEqual(
            resetPassword.getBytes(StandardCharsets.UTF_8),
            request.password().getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incorrect league reset password.");
    return resets.resetLeague();
  }
}
