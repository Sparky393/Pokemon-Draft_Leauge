package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.DraftService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DraftServiceTest {
  TeamRepository teams = mock(TeamRepository.class);
  PokemonRepository pokemon = mock(PokemonRepository.class);
  DraftPickRepository picks = mock(DraftPickRepository.class);
  RosterEntryRepository roster = mock(RosterEntryRepository.class);
  LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
  DraftService draft = new DraftService(teams, pokemon, picks, roster, settings);
  Team first;

  @BeforeEach void setup() {
    List<Team> league = new ArrayList<>();
    for (int i = 1; i <= 16; i++) {
      Team t = new Team("Team " + i, "Coach " + i, i <= 8 ? "A" : "B", i);
      org.springframework.test.util.ReflectionTestUtils.setField(t, "id", (long) i);
      league.add(t);
    }
    first = league.get(0);
    when(teams.findAll()).thenReturn(league);
    when(settings.findById(1L)).thenReturn(Optional.of(new LeagueSettings()));
    when(picks.count()).thenReturn(0L);
    when(roster.countByTeamId(1L)).thenReturn(0L);
    Pokemon candidate = new Pokemon("Test Pokemon", 5);
    org.springframework.test.util.ReflectionTestUtils.setField(candidate, "id", 99L);
    when(pokemon.findById(99L)).thenReturn(Optional.of(candidate));
    when(picks.save(any(DraftPick.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test void currentDraftTeamUsesDraftOrder() {
    assertEquals(first.getId(), draft.currentTeam().getId());
  }

  @Test void onlyCurrentTeamCanDraft() {
    assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
        () -> draft.pick(2L, false, 99L)).getStatusCode());
  }

  @Test void cannotExceedTenPokemon() {
    when(roster.countByTeamId(1L)).thenReturn(10L);
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> draft.pick(1L, false, 99L)).getStatusCode());
  }

  @Test void successfulPickRecordsPokemon() {
    DraftPick pick = draft.pick(1L, false, 99L);
    assertEquals(1, pick.getOverallPick());
    verify(roster).save(any(RosterEntry.class));
    verify(pokemon).save(argThat(Pokemon::isDrafted));
  }

  @Test void closedDraftRejectsPicks() {
    LeagueSettings config = new LeagueSettings();
    config.setDraftOpen(false);
    when(settings.findById(1L)).thenReturn(Optional.of(config));
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> draft.pick(1L, false, 99L)).getStatusCode());
  }
  @Test void refusesPickOverTheHundredDollarCap() {
    Pokemon existing = new Pokemon("Expensive", 99);
    when(roster.findByTeamId(1L)).thenReturn(List.of(new RosterEntry(1L, 41L)));
    when(pokemon.findById(41L)).thenReturn(Optional.of(existing));
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> draft.pick(1L, false, 99L));
    assertTrue(ex.getReason().contains("$100"));
    verify(picks, never()).save(any());
  }

  @Test void finalizedDraftRefusesNewPicksOrUndo() {
    LeagueSettings config = new LeagueSettings();
    config.setDraftFinalized(true);
    when(settings.findById(1L)).thenReturn(Optional.of(config));
    assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
        () -> draft.pick(1L, false, 99L)).getStatusCode());
    assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
        draft::undoLast).getStatusCode());
  }

}
