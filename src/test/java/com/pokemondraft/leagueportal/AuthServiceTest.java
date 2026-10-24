package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.AppUserRepository;
import com.pokemondraft.leagueportal.services.AuthService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {
  AppUserRepository users;
  AuthService auth;
  MockHttpSession session;
  AppUser coach;
  AppUser manager;

  @BeforeEach
  void setup() {
    users = mock(AppUserRepository.class);
    auth = new AuthService(users);
    session = new MockHttpSession();
    coach = new AppUser("coach1", "password", Role.COACH, 1L);
    manager = new AppUser("manager", "manager", Role.MANAGER, null);
    org.springframework.test.util.ReflectionTestUtils.setField(coach, "id", 1L);
    org.springframework.test.util.ReflectionTestUtils.setField(manager, "id", 2L);
    when(users.findByUsername("coach1")).thenReturn(Optional.of(coach));
    when(users.findById(1L)).thenReturn(Optional.of(coach));
    when(users.findById(2L)).thenReturn(Optional.of(manager));
    when(users.findResettableIdentities()).thenReturn(List.of(coach));
    when(users.findIdentityById(1L)).thenReturn(Optional.of(coach));
    when(users.findIdentityById(2L)).thenReturn(Optional.of(manager));
  }

  @Test
  void guestIdentityNeverReadsOrCreatesAnAccountAndCannotUseAccountHelpers() {
    session.setAttribute(AuthService.SESSION_USER_ID, manager.getId());
    var viewer = auth.enterGuest(session);
    assertEquals("GUEST", viewer.role());
    assertNull(viewer.id());
    assertNull(viewer.teamId());
    assertNull(session.getAttribute(AuthService.SESSION_USER_ID));
    // Even a stale account ID cannot grant privileges while the guest marker exists.
    session.setAttribute(AuthService.SESSION_USER_ID, manager.getId());
    assertEquals(HttpStatus.FORBIDDEN,
        assertThrows(ResponseStatusException.class, () -> auth.current(session)).getStatusCode());
    assertEquals(HttpStatus.FORBIDDEN,
        assertThrows(ResponseStatusException.class, () -> auth.manager(session)).getStatusCode());
    assertEquals(HttpStatus.FORBIDDEN,
        assertThrows(ResponseStatusException.class, () -> auth.teamId(session)).getStatusCode());
    assertEquals(HttpStatus.FORBIDDEN,
        assertThrows(ResponseStatusException.class, () -> auth.changePassword(session, "old", "newPassword"))
            .getStatusCode());
    verifyNoInteractions(users);
  }

  @Test
  void successfulCoachLoginLeavesGuestModeAndKeepsItsTeam() {
    auth.enterGuest(session);
    AppUser loggedIn = auth.login("coach1", "password", session);
    assertFalse(auth.isGuest(session));
    assertEquals(1L, loggedIn.getTeamId());
    assertEquals(Role.COACH, loggedIn.getRole());
    assertEquals(loggedIn.getId(), session.getAttribute(AuthService.SESSION_USER_ID));
  }

  @Test
  void loginUpgradesLegacyPasswordToHash() {
    auth.login("coach1", "password", session);
    assertNotEquals("password", coach.getPassword());
    assertTrue(coach.getPassword().startsWith("$2"));
    assertEquals(coach.getId(), session.getAttribute(AuthService.SESSION_USER_ID));
    verify(users).save(coach);
    auth.login("coach1", "password", session);
  }

  @Test
  void invalidLoginDoesNotAuthenticate() {
    assertEquals(
        HttpStatus.UNAUTHORIZED,
        assertThrows(ResponseStatusException.class, () -> auth.login("coach1", "wrong", session))
            .getStatusCode());
    assertNull(session.getAttribute(AuthService.SESSION_USER_ID));
  }

  @Test
  void changePasswordRequiresCurrentPasswordAndValidLength() {
    session.setAttribute(AuthService.SESSION_USER_ID, 1L);
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () -> auth.changePassword(session, "bad", "newPassword123"))
            .getStatusCode());
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () -> auth.changePassword(session, "password", "short"))
            .getStatusCode());
    auth.changePassword(session, "password", "newPassword123");
    assertNotEquals("newPassword123", coach.getPassword());
    assertEquals(
        HttpStatus.UNAUTHORIZED,
        assertThrows(
                ResponseStatusException.class,
                () -> auth.login("coach1", "password", new MockHttpSession()))
            .getStatusCode());
    auth.login("coach1", "newPassword123", new MockHttpSession());
  }

  @Test
  void onlyManagerCanResetCoachToHashedDefault() {
    session.setAttribute(AuthService.SESSION_USER_ID, 1L);
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(ResponseStatusException.class, () -> auth.resetPassword(session, 1L))
            .getStatusCode());
    session.setAttribute(AuthService.SESSION_USER_ID, 2L);
    String temporary = auth.resetPassword(session, 1L);
    assertNotEquals("password", temporary);
    assertTrue(temporary.length() >= 20);
    auth.login("coach1", temporary, new MockHttpSession());
    assertNotEquals("password", coach.getPassword());
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(ResponseStatusException.class, () -> auth.resetPassword(session, 2L))
            .getStatusCode());
    assertEquals(1, auth.resettableUsers(session).size());
    assertFalse(auth.resettableUsers(session).get(0).containsKey("password"));
  }

  @Test
  void unauthenticatedPasswordChangeIsRejected() {
    assertEquals(
        HttpStatus.UNAUTHORIZED,
        assertThrows(
                ResponseStatusException.class,
                () -> auth.changePassword(session, "password", "newPassword123"))
            .getStatusCode());
  }

  @Test
  void loginDoesNotRevealAccountExistence() {
    ResponseStatusException missing =
        assertThrows(
            ResponseStatusException.class, () -> auth.login("not-a-coach", "password", session));
    ResponseStatusException wrong =
        assertThrows(ResponseStatusException.class, () -> auth.login("coach1", "wrong", session));
    assertEquals(missing.getReason(), wrong.getReason());
    assertTrue(
        assertThrows(ResponseStatusException.class, () -> auth.login("", "password", session))
            .getReason()
            .contains("username"));
    assertTrue(
        assertThrows(ResponseStatusException.class, () -> auth.login("coach1", "", session))
            .getReason()
            .contains("password"));
    assertNull(session.getAttribute(AuthService.SESSION_USER_ID));
  }

  @Test
  void repeatedFailedLoginsAreThrottled() {
    for (int i = 0; i < 6; i++)
      assertEquals(
          HttpStatus.UNAUTHORIZED,
          assertThrows(ResponseStatusException.class, () -> auth.login("coach1", "wrong", session))
              .getStatusCode());
    assertEquals(
        HttpStatus.TOO_MANY_REQUESTS,
        assertThrows(ResponseStatusException.class, () -> auth.login("coach1", "password", session))
            .getStatusCode());
  }
}
