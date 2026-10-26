package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.dto.UserDto;
import com.pokemondraft.leagueportal.mapper.LeagueMapper;
import com.pokemondraft.leagueportal.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  public record LoginRequest(String username, String password) {
  }

  @PostMapping("/login")
  public UserDto login(@RequestBody LoginRequest req, HttpSession session,
      HttpServletRequest request) {
    var user = auth.login(req.username(), req.password(), session);
    request.changeSessionId(); // Avoid reusing a pre-login session identifier.
    return LeagueMapper.authUser(user);
  }

  @PostMapping("/guest")
  public UserDto guest(HttpSession session, HttpServletRequest request) {
    UserDto viewer = auth.enterGuest(session);
    request.changeSessionId();
    return viewer;
  }

  public record ChangePasswordRequest(String currentPassword, String newPassword) {
  }

  @PostMapping("/change-password")
  public void changePassword(@RequestBody ChangePasswordRequest request, HttpSession session) {
    auth.changePassword(session, request.currentPassword(), request.newPassword());
  }

  @GetMapping("/manager/users")
  public java.util.List<java.util.Map<String, Object>> resettableUsers(HttpSession session) {
    return auth.resettableUsers(session);
  }

  @PostMapping("/manager/users/{id}/reset-password")
  public Map<String, String> resetPassword(@PathVariable Long id, HttpSession session) {
    return Map.of("temporaryPassword", auth.resetPassword(session, id));
  }

  @GetMapping("/security")
  public Map<String, Boolean> passwordSecurity(HttpSession session) {
    return Map.of("usingInitialPassword", auth.isUsingInitialPassword(session));
  }

  @PostMapping("/logout")
  public void logout(HttpSession session) {
    session.invalidate();
  }

  @GetMapping("/me")
  public UserDto me(HttpSession session) {
    return auth.isGuest(session) ? auth.viewer(session)
        : LeagueMapper.authUser(auth.current(session));
  }
}
