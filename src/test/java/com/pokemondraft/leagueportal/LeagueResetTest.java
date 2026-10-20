package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LeagueResetTest {
  @Test void resetClearsOnlyPostDraftStateAndPreservesPreDraftSetup() {
    TeamRepository teams = mock(TeamRepository.class);
    PokemonRepository pokemon = mock(PokemonRepository.class);
    RosterEntryRepository roster = mock(RosterEntryRepository.class);
    DraftPickRepository picks = mock(DraftPickRepository.class);
    WeeklySubmissionRepository submissions = mock(WeeklySubmissionRepository.class);
    TransactionRecordRepository transactions = mock(TransactionRecordRepository.class);
    FreeAgentBidRepository bids = mock(FreeAgentBidRepository.class);
    TradeOfferRepository trades = mock(TradeOfferRepository.class);
    MatchupRepository matchups = mock(MatchupRepository.class);
    LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
    WeekLockRepository locks = mock(WeekLockRepository.class);
    ScheduleService schedule = mock(ScheduleService.class);

    TestRosterService service = new TestRosterService(teams, pokemon, roster, picks,
        submissions, transactions, bids, trades, matchups, settings, locks, schedule);
    Team team = new Team("Burn", "Coach Burn", "A", 7);
    team.setFaBudget(1);
    team.setWins(4);
    team.setLosses(2);
    team.setDifferential(10);
    team.setManualRank(1);
    team.setLogoContentType("image/png");
    team.setLogoBase64("example");
    team.setOfficialSlot(1);

    Pokemon sneasler = new Pokemon("Sneasler", 17);
    sneasler.setDrafted(true);
    Pokemon dragonite = new Pokemon("Dragonite", 19);
    Matchup regular = new Matchup(1, 1L, 2L);
    regular.setPlayed(true);
    regular.setHomeScore(3);
    regular.setAwayScore(2);
    Matchup playoff = new Matchup(14, 1L, 2L);
    playoff.setPlayed(true);
    LeagueSettings league = new LeagueSettings();
    league.setCurrentWeek(14);
    league.setDraftOpen(false);
    league.setWaiverProcessed(true);
    league.setLineupDeadline(java.time.LocalDateTime.now());
    when(roster.count()).thenReturn(1L);
    when(teams.findAll()).thenReturn(List.of(team));
    when(pokemon.findAll()).thenReturn(List.of(sneasler, dragonite));
    when(matchups.findAll()).thenReturn(List.of(regular, playoff));
    when(settings.findById(1L)).thenReturn(Optional.of(league));

    service.resetLeague();

    verify(bids).deleteAllInBatch();
    verify(trades).deleteAllInBatch();
    verify(transactions).deleteAllInBatch();
    verify(submissions).deleteAllInBatch();
    verify(picks).deleteAllInBatch();
    verify(roster).deleteAllInBatch();
    verify(locks).deleteAllInBatch();
    verify(matchups).delete(playoff);
    verify(matchups, never()).delete(regular);
    verify(schedule).syncOfficialSchedule();

    assertFalse(sneasler.isDrafted());
    assertEquals(17, sneasler.getPrice());
    assertEquals("Sneasler", sneasler.getName());
    assertEquals("Dragonite", dragonite.getName());
    assertFalse(regular.isPlayed());
    assertNull(regular.getHomeScore());
    assertNull(regular.getAwayScore());
    assertEquals("Burn", team.getName());
    assertEquals("Coach Burn", team.getCoachName());
    assertEquals("A", team.getDivision());
    assertEquals(7, team.getDraftPosition());
    assertEquals(1, team.getOfficialSlot());
    assertEquals("example", team.getLogoBase64());
    assertEquals(0, team.getWins());
    assertEquals(0, team.getManualRank());
    assertEquals(3, team.getFaBudget());
    assertEquals(1, league.getCurrentWeek());
    assertTrue(league.isDraftOpen());
    assertFalse(league.isWaiverProcessed());
    assertNull(league.getLineupDeadline());
  }
}
