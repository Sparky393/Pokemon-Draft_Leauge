package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pokemondraft.leagueportal.controllers.AuthController;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.services.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthControllerTest {
  @Test void changePasswordDelegatesWithAuthenticatedSession() {
    AuthService service = mock(AuthService.class);
    AuthController controller = new AuthController(service);
    MockHttpSession session = new MockHttpSession();
    controller.changePassword(new AuthController.ChangePasswordRequest("oldPassword", "newPassword"), session);
    verify(service).changePassword(session, "oldPassword", "newPassword");
  }

  @Test void managerResetUsesTargetIdAndManagerSession() {
    AuthService service = mock(AuthService.class);
    AuthController controller = new AuthController(service);
    MockHttpSession session = new MockHttpSession();
    when(service.resetPassword(session, 5L)).thenReturn("test-temp-password");
    assertEquals("test-temp-password",controller.resetPassword(5L, session).get("temporaryPassword"));
    verify(service).resetPassword(session, 5L);
    controller.resettableUsers(session);
    verify(service).resettableUsers(session);
  }

  @Test void loginAndMeNeverReturnPassword() {
    AuthService service = mock(AuthService.class);
    AuthController controller = new AuthController(service);
    MockHttpSession session = new MockHttpSession();
    AppUser user = new AppUser("coach1", "secretPassword", Role.COACH, 1L);
    when(service.login("coach1", "secretPassword", session)).thenReturn(user);
    when(service.current(session)).thenReturn(user);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSession(session);
    assertEquals("coach1", controller.login(new AuthController.LoginRequest("coach1", "secretPassword"), session, request).username());
    assertEquals("coach1", controller.me(session).username());
    assertFalse(java.util.Arrays.stream(controller.me(session).getClass().getRecordComponents())
        .anyMatch(c -> c.getName().equalsIgnoreCase("password")));
  }
}
