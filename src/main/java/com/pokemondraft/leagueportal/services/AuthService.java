package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  public static final String SESSION_USER_ID = "userId";
  private final AppUserRepository users;
  private static final int MAX_FAILURES = 6;
  private static final long WINDOW_MILLIS = 10 * 60 * 1000;
  private final Map<String, FailureWindow> failures = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();

  private static class FailureWindow {
    long started = System.currentTimeMillis();
    int count;

    synchronized boolean locked() {
      if (System.currentTimeMillis() - started > WINDOW_MILLIS) {
        count = 0;
        started = System.currentTimeMillis();
      }
      return count >= MAX_FAILURES;
    }

    synchronized void failed() {
      if (!locked()) count++;
    }
  }

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public AuthService(AppUserRepository users) {
    this.users = users;
  }

  @Transactional
  public AppUser login(String username, String password, HttpSession session) {
    if (username == null || username.isBlank())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your username.");
    if (password == null || password.isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your password.");

    String key = username.trim().toLowerCase(Locale.ROOT);
    // Bound memory when many fabricated usernames are submitted by a bot.
    if (failures.size() >= 4096) {
      failures
          .entrySet()
          .removeIf(e -> System.currentTimeMillis() - e.getValue().started > WINDOW_MILLIS);
      if (failures.size() >= 4096 && !failures.containsKey(key))
        throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts right now. Try again later.");
    }
    FailureWindow window = failures.computeIfAbsent(key, k -> new FailureWindow());
    if (window.locked())
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Try again in 10 minutes.");

    AppUser user = users.findByUsername(username.trim()).orElse(null);
    if (user == null || !matches(password, user.getPassword())) {
      window.failed();
      // Do not reveal which accounts exist on a public website.
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "Username or password is incorrect. Check your details and try again.");
    }
    failures.remove(key);
    if (!isHashed(user.getPassword())) {
      user.setPassword(encoder.encode(password));
      users.save(user);
    }
    session.setAttribute(SESSION_USER_ID, user.getId());
    return user;
  }

  private boolean isHashed(String value) {
    return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
  }

  private boolean matches(String supplied, String stored) {
    return stored != null
        && (isHashed(stored) ? encoder.matches(supplied, stored) : stored.equals(supplied));
  }

  @Transactional
  public void changePassword(HttpSession session, String oldPassword, String newPassword) {
    AppUser user = current(session);
    if (oldPassword == null || !matches(oldPassword, user.getPassword()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
    validatePassword(newPassword);
    if (matches(newPassword, user.getPassword()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a different password");
    user.setPassword(encoder.encode(newPassword));
    users.save(user);
  }

  /** Return a fresh one-time secret only to the authenticated commissioner. */
  @Transactional
  public String resetPassword(HttpSession session, Long userId) {
    manager(session);
    AppUser target =
        users
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    if (target.getRole() == Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot reset manager accounts");
    byte[] value = new byte[18];
    random.nextBytes(value);
    String temporaryPassword =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    target.setPassword(encoder.encode(temporaryPassword));
    users.save(target);
    return temporaryPassword;
  }

  public java.util.List<java.util.Map<String, Object>> resettableUsers(HttpSession session) {
    manager(session);
    return users.findAll().stream()
        .filter(u -> u.getRole() != Role.MANAGER)
        .map(
            u ->
                java.util.Map.<String, Object>of(
                    "id", u.getId(), "username", u.getUsername(), "role", u.getRole().name()))
        .toList();
  }

  /** Read-only security warning; never changes a coach's existing password. */
  public boolean isUsingInitialPassword(HttpSession session) {
    AppUser u = current(session);
    String initial = u.getRole() == Role.MANAGER ? "manager" : "password";
    return matches(initial, u.getPassword());
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8 || password.length() > 72 || password.isBlank())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 8–72 characters");
  }

  public AppUser current(HttpSession session) {
    Object id = session.getAttribute(SESSION_USER_ID);
    if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in");
    return users
        .findById((Long) id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired"));
  }

  public AppUser manager(HttpSession session) {
    AppUser u = current(session);
    if (u.getRole() != Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager only");
    return u;
  }

  public Long teamId(HttpSession session) {
    AppUser u = current(session);
    if (u.getRole() == Role.MANAGER) return null;
    if (u.getTeamId() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No team assigned");
    return u.getTeamId();
  }
}
