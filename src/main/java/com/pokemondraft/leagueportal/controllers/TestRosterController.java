package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.services.AuthService;
import com.pokemondraft.leagueportal.services.TestRosterService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager/testing")
public class TestRosterController {
  private final AuthService auth;
  private final TestRosterService testRosters;

  public TestRosterController(AuthService auth, TestRosterService testRosters) {
    this.auth = auth;
    this.testRosters = testRosters;
  }

  @PostMapping("/fill-rosters")
  public Map<String, Integer> fillRosters(HttpSession session) {
    auth.manager(session);
    return testRosters.fillTestRosters();
  }

}
