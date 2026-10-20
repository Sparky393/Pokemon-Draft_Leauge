package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pokemondraft.leagueportal.controllers.LeagueAdministrationController;
import com.pokemondraft.leagueportal.services.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LeagueResetPasswordTest {
  private final AuthService auth=mock(AuthService.class);
  private final TestRosterService resets=mock(TestRosterService.class);
  private final WeekLockService locks=mock(WeekLockService.class);
  private final HttpSession session=mock(HttpSession.class);

  @Test void wrongPasswordNeverResets() {
    var controller=new LeagueAdministrationController(auth,resets,locks);
    var ex=assertThrows(ResponseStatusException.class, () ->
        controller.reset(new LeagueAdministrationController.ResetRequest("incorrect"),session));
    assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());
    verify(auth).manager(session);
    verifyNoInteractions(resets);
  }

  @Test void blankPasswordNeverResets() {
    var controller=new LeagueAdministrationController(auth,resets,locks);
    var ex=assertThrows(ResponseStatusException.class, () ->
        controller.reset(new LeagueAdministrationController.ResetRequest(""),session));
    assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());
    verifyNoInteractions(resets);
  }

  @Test void correctPasswordRequiresManagerAndRunsReset() {
    var controller=new LeagueAdministrationController(auth,resets,locks);
    controller.reset(new LeagueAdministrationController.ResetRequest("password"),session);
    verify(auth).manager(session);
    verify(resets).resetLeague();
  }
}
