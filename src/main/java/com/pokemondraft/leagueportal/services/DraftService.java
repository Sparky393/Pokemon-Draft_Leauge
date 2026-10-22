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
    List<Team> order = teams.findAllMetadata();
    return currentTeam(order, picks.count(), settings.findById(1L).orElseGet(LeagueSettings::new));
  }

  public Team currentTeam(List<Team> order, long count, LeagueSettings league) {
    order = new ArrayList<>(order);
    order.sort(Comparator.comparingInt(Team::getDraftPosition));
    requireTeams(order.size());
    if (count >= 160) return null;
    return order.get(currentSlot(count, league.isSnakeDraft()));
  }

  /** Polls fetch only IDs and positions, never full teams or logo text. */
  public Long currentTeamId(long count, LeagueSettings league) {
    return currentTeamId(count, league.isSnakeDraft());
  }

  public Long currentTeamId(long count, boolean snake) {
    List<TeamRepository.DraftOrder> order = teams.findAllProjectedByOrderByDraftPositionAsc();
    requireTeams(order.size());
    return count >= 160 ? null : order.get(currentSlot(count, snake)).getId();
  }

  private int currentSlot(long count, boolean snake) {
    int round = (int) (count / 16), slot = (int) (count % 16);
    return snake && round % 2 == 1 ? 15 - slot : slot;
  }

  private void requireTeams(int count) {
    if (count != 16)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "The draft cannot start until the commissioner has configured all 16 teams.");
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
    long pickCount = picks.count();
    Team current = currentTeam(teams.findAllMetadata(), pickCount, s);
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
    int currentValue = Math.toIntExact(roster.totalPriceByTeamId(current.getId()));
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
    int overall = (int) pickCount + 1;
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
    List<RosterEntry> entries = roster.findAll();
    Set<Long> ids = new HashSet<>();
    for (RosterEntry e : entries) ids.add(e.getPokemonId());
    Map<Long, Pokemon> mons = new HashMap<>();
    if (!ids.isEmpty()) for (Pokemon p : pokemon.findAllById(ids)) mons.put(p.getId(), p);
    Map<Long, List<RosterEntry>> byTeam = new HashMap<>();
    for (RosterEntry e : entries)
      byTeam.computeIfAbsent(e.getTeamId(), k -> new ArrayList<>()).add(e);
    for (Team t : teams.findAllMetadata()) {
      List<RosterEntry> members = byTeam.getOrDefault(t.getId(), List.of());
      int value = 0;
      for (RosterEntry e : members) {
        Pokemon p = mons.get(e.getPokemonId());
        if (p == null) throw new NoSuchElementException();
        value += p.getPrice();
      }
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
    DraftPick last = picks.findFirstByOrderByOverallPickDesc().orElse(null);
    if (last == null) return;
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
