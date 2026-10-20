package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DraftService {
  private final TeamRepository teams;
  private final PokemonRepository pokemon;
  private final DraftPickRepository picks;
  private final RosterEntryRepository roster;
  private final LeagueSettingsRepository settings;

  public DraftService(
      TeamRepository teams,
      PokemonRepository pokemon,
      DraftPickRepository picks,
      RosterEntryRepository roster,
      LeagueSettingsRepository settings) {
    this.teams = teams;
    this.pokemon = pokemon;
    this.picks = picks;
    this.roster = roster;
    this.settings = settings;
  }

  public Team currentTeam() {
    List<Team> order = teams.findAll();
    order.sort(Comparator.comparingInt(Team::getDraftPosition));
    if (order.size() != 16)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "The draft cannot start until the commissioner has configured all 16 teams.");
    long count = picks.count();
    if (count >= 160) return null;
    int round = (int) (count / 16);
    int slot = (int) (count % 16);
    boolean snake = settings.findById(1L).orElseGet(LeagueSettings::new).isSnakeDraft();
    if (snake && round % 2 == 1) slot = 15 - slot;
    return order.get(slot);
  }

  @Transactional
  public DraftPick pick(Long actingTeamId, boolean manager, Long pokemonId) {
    // Lock one shared database row before reading the next pick number.
    // A second simultaneous pick waits until the first transaction commits.
    LeagueSettings s =
        settings
            .lockForDraft()
            .orElseGet(() -> settings.findById(1L).orElseGet(LeagueSettings::new));
    if (s.isDraftFinalized())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The official draft is finalized. Draft picks cannot be changed.");
    if (!s.isDraftOpen())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "The commissioner has closed the draft. You cannot make picks right now.");
    Team current = currentTeam();
    if (current == null)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "All 160 draft picks are complete. No more Pokémon can be selected.");
    if (!manager && !current.getId().equals(actingTeamId))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "It is "
              + current.getName()
              + "\'s turn to draft. Wait for your team to be on the clock.");
    if (pokemonId == null)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose a Pokémon before drafting.");
    Pokemon p =
        pokemon
            .findById(pokemonId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That Pokémon is no longer in the draft pool."));
    if (p.isDrafted() || roster.findByPokemonId(pokemonId).isPresent())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          p.getName() + " has already been drafted. Select an available Pokémon.");
    if (roster.countByTeamId(current.getId()) >= 10)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          current.getName() + " already has 10 Pokémon and cannot draft another.");
    int currentValue = 0;
    for (RosterEntry entry : roster.findByTeamId(current.getId()))
      currentValue += pokemon.findById(entry.getPokemonId()).map(Pokemon::getPrice).orElse(0);
    int newTotal = currentValue + p.getPrice();
    if (newTotal > 100)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          p.getName()
              + " costs $"
              + p.getPrice()
              + ". "
              + current.getName()
              + " has $"
              + (100 - currentValue)
              + " left in its $100 draft budget"
              + " (this pick would make the roster $"
              + newTotal
              + ").");
    int overall = (int) picks.count() + 1;
    int round = (overall - 1) / 16 + 1;
    p.setDrafted(true);
    pokemon.save(p);
    roster.save(new RosterEntry(current.getId(), p.getId()));
    return picks.save(new DraftPick(overall, round, current.getId(), p.getId()));
  }

  /** Finalize only after validating the full, legally-priced draft. */
  @Transactional
  public void finalizeDraft() {
    LeagueSettings league =
        settings.lockForDraft().orElseGet(() -> settings.findById(1L).orElseThrow());
    if (league.isDraftFinalized()) return;
    if (picks.count() != 160)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "The draft needs all 160 picks before it can be finalized (" + picks.count() + "/160).");
    for (Team t : teams.findAll()) {
      List<RosterEntry> members = roster.findByTeamId(t.getId());
      int value = 0;
      for (RosterEntry e : members)
        value += pokemon.findById(e.getPokemonId()).orElseThrow().getPrice();
      if (members.size() != 10 || value > 100)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            t.getName()
                + " must have exactly 10 Pokémon and a roster value at or below $100"
                + " before finalizing (currently "
                + members.size()
                + " Pokémon, $"
                + value
                + ").");
    }
    league.setDraftFinalized(true);
    league.setDraftOpen(false);
    settings.save(league);
  }

  @Transactional
  public void undoLast() {
    LeagueSettings league =
        settings
            .lockForDraft()
            .orElseGet(() -> settings.findById(1L).orElseGet(LeagueSettings::new));
    if (league.isDraftFinalized())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The official draft has been finalized. Undo is disabled.");
    List<DraftPick> all = picks.findAllByOrderByOverallPickAsc();
    if (all.isEmpty()) return;
    DraftPick last = all.get(all.size() - 1);
    roster.findByPokemonId(last.getPokemonId()).ifPresent(roster::delete);
    pokemon
        .findById(last.getPokemonId())
        .ifPresent(
            p -> {
              p.setDrafted(false);
              pokemon.save(p);
            });
    picks.delete(last);
  }
}
