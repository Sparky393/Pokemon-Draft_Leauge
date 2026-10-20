package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pokemondraft.leagueportal.controllers.TeamBrandingController;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.TeamRepository;
import com.pokemondraft.leagueportal.services.AuthService;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class TeamBrandingControllerTest {
  AuthService auth = mock(AuthService.class);
  TeamRepository teams = mock(TeamRepository.class);
  TeamBrandingController controller = new TeamBrandingController(auth, teams);
  MockHttpSession session = new MockHttpSession();
  Team mine = new Team("Burn", "Bingus", "A", 1);
  Team other = new Team("Wide Right", "wide_right_", "B", 2);

  @BeforeEach
  void setup() {
    // JPA assigns IDs in production; use a real persisted-style entity in tests.
    setId(mine, 1L);
    setId(other, 2L);
    when(auth.current(session)).thenReturn(new AppUser("Bingus", "secret", Role.COACH, 1L));
    when(teams.findById(1L)).thenReturn(Optional.of(mine));
    when(teams.findById(2L)).thenReturn(Optional.of(other));
    when(teams.findAll()).thenReturn(List.of(mine, other));
    when(teams.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static void setId(Team team, Long id) {
    try {
      var field = Team.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(team, id);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void coachRenamesOnlyTheirOwnTeam() {
    Team changed =
        controller.rename(new TeamBrandingController.TeamNameRequest("  New Burn  "), session);
    assertEquals("New Burn", changed.getName());
    assertEquals("Wide Right", other.getName());
    verify(teams).save(mine);
  }

  @Test
  void managerCannotRenameOrUploadTeamBranding() throws Exception {
    when(auth.current(session)).thenReturn(new AppUser("manager", "secret", Role.MANAGER, null));
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(
                ResponseStatusException.class,
                () -> controller.rename(new TeamBrandingController.TeamNameRequest("New"), session))
            .getStatusCode());
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.upload(
                        new MockMultipartFile("file", "image.png", "image/png", new byte[] {1}),
                        session))
            .getStatusCode());
    verify(teams, never()).save(any());
  }

  @Test
  void duplicateAndInvalidNamesAreRejected() {
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.rename(
                        new TeamBrandingController.TeamNameRequest("wide right"), session))
            .getStatusCode());
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () -> controller.rename(new TeamBrandingController.TeamNameRequest(" "), session))
            .getStatusCode());
    verify(teams, never()).save(any());
  }

  @Test
  void logoIsStoredInDatabaseAndNotSerializedInTeamLists() throws Exception {
    byte[] png =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9YueWUkAAAAASUVORK5CYII=");
    controller.upload(new MockMultipartFile("file", "logo.png", "image/png", png), session);
    assertEquals("image/png", mine.getLogoContentType());
    assertEquals(Base64.getEncoder().encodeToString(png), mine.getLogoBase64());
    assertTrue(mine.getLogoUrl().startsWith("/api/teams/1/logo?v="));
    assertArrayEquals(png, controller.logo(1L, session).getBody());
    assertNotNull(
        Team.class
            .getMethod("getLogoBase64")
            .getAnnotation(com.fasterxml.jackson.annotation.JsonIgnore.class),
        "Team JSON must never include raw base64 image data");
    assertNotNull(
        Team.class.getMethod("getLogoUrl"), "Team JSON must expose a lightweight image URL");
    controller.removeLogo(session);
    assertNull(mine.getLogoUrl());
    assertNull(mine.getLogoBase64());
  }

  @Test
  void rejectsFakeImageAndOversizedUpload() {
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.upload(
                        new MockMultipartFile(
                            "file", "fake.png", "image/png", "this is not an image".getBytes()),
                        session))
            .getStatusCode());
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.upload(
                        new MockMultipartFile(
                            "file", "big.png", "image/png", new byte[1024 * 1024 + 1]),
                        session))
            .getStatusCode());
  }
}
