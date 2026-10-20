package com.pokemondraft.leagueportal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** API smoke tests run against a throwaway H2 database, never Neon. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:workflowtest;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class LeagueWorkflowSmokeTest {
  @Autowired MockMvc mvc;

  private MockHttpSession coach() throws Exception {
    MockHttpSession session = new MockHttpSession();
    mvc.perform(post("/api/auth/login").session(session)
        .header("X-Requested-With","XMLHttpRequest")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"coach1\",\"password\":\"password\"}"))
        .andExpect(status().isOk());
    return session;
  }

  @Test void browserMayLoginAndDownloadEachReadOnlyCsv() throws Exception {
    MockHttpSession session = coach();
    for (String kind : new String[]{"draft","rosters","standings","schedule","transactions"}) {
      mvc.perform(get("/api/exports/"+kind).session(session))
          .andExpect(status().isOk())
          .andExpect(header().string("Content-Disposition",
              "attachment; filename=\"league-"+kind+".csv\""))
          .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }
    mvc.perform(get("/api/notifications").session(session))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    mvc.perform(get("/api/draft/status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pickCount").value(0));
  }

  @Test void crossSiteFormCannotChangeLeagueThroughApi() throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"coach1\",\"password\":\"password\"}"))
        .andExpect(status().isForbidden());
  }
}
