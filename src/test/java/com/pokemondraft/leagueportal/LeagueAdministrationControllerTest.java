package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pokemondraft.leagueportal.controllers.LeagueAdministrationController;
import com.pokemondraft.leagueportal.services.AuthService;
import com.pokemondraft.leagueportal.services.TestRosterService;
import com.pokemondraft.leagueportal.services.WeekLockService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

class LeagueAdministrationControllerTest {
  AuthService auth = mock(AuthService.class);
  TestRosterService resets = mock(TestRosterService.class);
  WeekLockService locks = mock(WeekLockService.class);
  LeagueAdministrationController controller =
      new LeagueAdministrationController(auth, resets, locks);
  MockHttpSession session = new MockHttpSession();

  @Test
  void resetRequiresPasswordAndManager() {
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.reset(
                        new LeagueAdministrationController.ResetRequest("wrong"), session))
            .getStatusCode());
    verify(resets, never()).resetLeague();
    controller.reset(new LeagueAdministrationController.ResetRequest("password"), session);
    verify(resets).resetLeague();
    verify(auth, times(2)).manager(session);
  }

  @Test
  void coachesCannotLockWeeksOrResetLeague() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(auth).manager(session);
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.reset(
                        new LeagueAdministrationController.ResetRequest("RESET ENTIRE LEAGUE"),
                        session))
            .getStatusCode());
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.lockWeek(
                        1, new LeagueAdministrationController.WeekLockRequest(true), session))
            .getStatusCode());
    verifyNoInteractions(resets, locks);
  }

  @Test
  void weekLockRequiresManagerAndDelegates() {
    controller.lockWeek(1, new LeagueAdministrationController.WeekLockRequest(true), session);
    verify(auth).manager(session);
    verify(locks).setLocked(1, true);
  }
}
