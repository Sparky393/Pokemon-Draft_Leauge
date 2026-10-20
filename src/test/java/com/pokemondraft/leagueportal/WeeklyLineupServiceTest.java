package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.WeeklyLineupService;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WeeklyLineupServiceTest {
  WeeklySubmissionRepository submissions = mock(WeeklySubmissionRepository.class);
  RosterEntryRepository roster = mock(RosterEntryRepository.class);
  MatchupRepository matchups = mock(MatchupRepository.class);
  LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
  PokemonRepository pokemon = mock(PokemonRepository.class);
  WeeklyLineupService service =
      new WeeklyLineupService(submissions, roster, matchups, settings, pokemon);
  LeagueSettings config = new LeagueSettings();
  List<Long> six = List.of(10L, 11L, 12L, 13L, 14L, 15L);

  @BeforeEach
  void setup() {
    when(settings.findById(1L)).thenReturn(Optional.of(config));
    when(matchups.findAllByOrderByWeekAscIdAsc()).thenReturn(List.of(new Matchup(1, 1L, 2L)));
    when(roster.findByTeamId(1L))
        .thenReturn(six.stream().map(id -> new RosterEntry(1L, id)).toList());
    when(submissions.findByTeamIdAndWeek(anyLong(), anyInt())).thenReturn(Optional.empty());
    when(submissions.save(any(WeeklySubmission.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void rejectsDuplicateOrIncompleteLineup() {
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () -> service.submit(1L, List.of(10L, 10L, 12L, 13L, 14L, 15L)))
            .getStatusCode());
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(ResponseStatusException.class, () -> service.submit(1L, List.of(10L, 11L)))
            .getStatusCode());
  }

  @Test
  void acceptsSixOwnedPokemon() {
    WeeklySubmission result = service.submit(1L, six);
    assertEquals("10,11,12,13,14,15", result.getPokemonIdsCsv());
    verify(submissions).save(any(WeeklySubmission.class));
  }

  @Test
  void rejectsNonOwnedPokemon() {
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(
                ResponseStatusException.class,
                () -> service.submit(1L, List.of(10L, 11L, 12L, 13L, 14L, 99L)))
            .getStatusCode());
  }

  @Test
  void rejectsAfterDeadline() {
    config.setLineupDeadline(
        LocalDateTime.now(java.time.ZoneId.of("America/New_York")).minusMinutes(1));
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(ResponseStatusException.class, () -> service.submit(1L, six)).getStatusCode());
  }

  @Test
  void rejectsLineupsDuringFreeAgencyAndBothByes() {
    for (int week : List.of(4, 8, 9)) {
      config.setCurrentWeek(week);
      assertEquals(
          HttpStatus.BAD_REQUEST,
          assertThrows(ResponseStatusException.class, () -> service.submit(1L, six))
              .getStatusCode());
      assertFalse(service.hasMatchup(1L, week));
    }
    verify(submissions, never()).save(any(WeeklySubmission.class));
  }

  @Test
  void bothSubmittedLineupsCannotBeEditedThroughEitherApi() {
    when(submissions.findByTeamIdAndWeek(1L, 1))
        .thenReturn(Optional.of(new WeeklySubmission(1L, 1, "10,11,12,13,14,15")));
    when(submissions.findByTeamIdAndWeek(2L, 1))
        .thenReturn(Optional.of(new WeeklySubmission(2L, 1, "20,21,22,23,24,25")));
    ResponseStatusException error =
        assertThrows(ResponseStatusException.class, () -> service.submit(1L, six));
    assertTrue(error.getReason().toLowerCase().contains("locked"));
    verify(submissions, never()).save(any(WeeklySubmission.class));
  }

  @Test
  void missedDeadlineCarriesForwardMostRecentLineupAcrossByeWeeks() {
    config.setCurrentWeek(5);
    config.setLineupDeadline(
        LocalDateTime.now(java.time.ZoneId.of("America/New_York")).minusMinutes(1));
    when(matchups.findAllByOrderByWeekAscIdAsc()).thenReturn(List.of(new Matchup(5, 1L, 2L)));
    when(roster.findByTeamId(2L))
        .thenReturn(
            List.of(20L, 21L, 22L, 23L, 24L, 25L).stream()
                .map(id -> new RosterEntry(2L, id))
                .toList());
    when(submissions.findAll())
        .thenReturn(
            List.of(
                new WeeklySubmission(1L, 1, "15,14,13,12,11,10"),
                new WeeklySubmission(1L, 3, "10,12,14,11,13,15")));
    service.finalizeExpired(5);
    var captor = org.mockito.ArgumentCaptor.forClass(WeeklySubmission.class);
    verify(submissions, times(2)).save(captor.capture());
    WeeklySubmission carried =
        captor.getAllValues().stream()
            .filter(ws -> ws.getTeamId().equals(1L))
            .findFirst()
            .orElseThrow();
    assertEquals("10,12,14,11,13,15", carried.getPokemonIdsCsv());
    assertTrue(carried.isAutomatic());
    assertEquals(5, carried.getWeek());
  }

  @Test
  void missedDeadlineReplacesDroppedPokemonWithoutRandomSelection() {
    config.setCurrentWeek(2);
    config.setLineupDeadline(
        LocalDateTime.now(java.time.ZoneId.of("America/New_York")).minusMinutes(1));
    when(matchups.findAllByOrderByWeekAscIdAsc()).thenReturn(List.of(new Matchup(2, 1L, 2L)));
    when(roster.findByTeamId(1L))
        .thenReturn(
            List.of(10L, 11L, 12L, 13L, 14L, 16L, 17L).stream()
                .map(id -> new RosterEntry(1L, id))
                .toList());
    when(roster.findByTeamId(2L))
        .thenReturn(
            List.of(20L, 21L, 22L, 23L, 24L, 25L).stream()
                .map(id -> new RosterEntry(2L, id))
                .toList());
    when(submissions.findAll())
        .thenReturn(List.of(new WeeklySubmission(1L, 1, "15,14,13,12,11,10")));
    service.finalizeExpired(2);
    var captor = org.mockito.ArgumentCaptor.forClass(WeeklySubmission.class);
    verify(submissions, times(2)).save(captor.capture());
    WeeklySubmission carried =
        captor.getAllValues().stream()
            .filter(ws -> ws.getTeamId().equals(1L))
            .findFirst()
            .orElseThrow();
    assertEquals("14,13,12,11,10,16", carried.getPokemonIdsCsv());
    assertTrue(carried.isAutomatic());
  }

  @Test
  void firstMissedDeadlineUsesStableOwnedPokemonAndMarksAutomatic() {
    config.setCurrentWeek(2);
    config.setLineupDeadline(
        LocalDateTime.now(java.time.ZoneId.of("America/New_York")).minusMinutes(1));
    when(matchups.findAllByOrderByWeekAscIdAsc()).thenReturn(List.of(new Matchup(2, 1L, 2L)));
    when(roster.findByTeamId(2L))
        .thenReturn(
            List.of(20L, 21L, 22L, 23L, 24L, 25L).stream()
                .map(id -> new RosterEntry(2L, id))
                .toList());
    service.finalizeExpired(2);
    var captor = org.mockito.ArgumentCaptor.forClass(WeeklySubmission.class);
    verify(submissions, times(2)).save(captor.capture());
    WeeklySubmission auto =
        captor.getAllValues().stream()
            .filter(ws -> ws.getTeamId().equals(1L))
            .findFirst()
            .orElseThrow();
    assertEquals("10,11,12,13,14,15", auto.getPokemonIdsCsv());
    assertTrue(auto.isAutomatic());
  }
}
