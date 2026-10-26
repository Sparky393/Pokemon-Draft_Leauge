package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;

import com.pokemondraft.leagueportal.exception.ApiExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApiErrorMessagesTest {
  @Test
  void leagueRuleErrorReturnsSpecificReasonAsJsonMessage() {
    var handler = new ApiExceptionHandler();
    var response = handler.leagueRule(new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Free agency opens in Week 4. The league is in Week 2."));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals(Map.of("message", "Free agency opens in Week 4. The league is in Week 2."),
        response.getBody());
  }

  @Test
  void authenticationErrorsPreserveSpecificReason() {
    var response = new ApiExceptionHandler()
        .leagueRule(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password."));
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertEquals("Incorrect password.", response.getBody().get("message"));
  }

  @Test
  void missingReasonGivesFriendlyFallback() {
    var response = new ApiExceptionHandler()
        .leagueRule(new ResponseStatusException(HttpStatus.FORBIDDEN));
    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    assertEquals("You do not have permission to perform this action.",
        response.getBody().get("message"));
  }
}
