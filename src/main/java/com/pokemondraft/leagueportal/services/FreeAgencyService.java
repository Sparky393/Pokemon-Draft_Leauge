package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FreeAgencyService {
  private static final int ROSTER_LIMIT = 10, ROSTER_BUDGET = 100;
  private static final ZoneId EASTERN = ZoneId.of("America/New_York");
  private static final LocalDateTime[] CLOSES = { LocalDateTime.of(2026, 11, 28, 19, 0),
      LocalDateTime.of(2026, 12, 1, 19, 0) };
  private static final LocalDateTime[] OPENS = { LocalDateTime.of(2026, 11, 25, 0, 0),
      LocalDateTime.of(2026, 11, 28, 19, 0) };
  private final Clock clock;
  private final FreeAgentBidRepository bids;
  private final TeamRepository teams;
  private final PokemonRepository pokemon;
  private final RosterEntryRepository roster;
  private final TransactionRecordRepository transactions;
  private final TradeOfferRepository trades;
  private final MatchupRepository matchups;
  private final LeagueSettingsRepository settings;

  @Autowired
  public FreeAgencyService(FreeAgentBidRepository bids, TeamRepository teams,
      PokemonRepository pokemon, RosterEntryRepository roster,
      TransactionRecordRepository transactions, TradeOfferRepository trades,
      MatchupRepository matchups, LeagueSettingsRepository settings) {
    this(bids, teams, pokemon, roster, transactions, trades, matchups, settings,
        Clock.system(EASTERN));
  }

  public FreeAgencyService(FreeAgentBidRepository bids, TeamRepository teams,
      PokemonRepository pokemon, RosterEntryRepository roster,
      TransactionRecordRepository transactions, TradeOfferRepository trades,
      MatchupRepository matchups, LeagueSettingsRepository settings, Clock clock) {
    this.clock = clock;
    this.bids = bids;
    this.teams = teams;
    this.pokemon = pokemon;
    this.roster = roster;
    this.transactions = transactions;
    this.trades = trades;
    this.matchups = matchups;
    this.settings = settings;
  }

  /** Two Week 4 waiver periods; each team retains its original $3 total budget. */
  public int waiverPeriodsProcessed() {
    return (int) transactions.countByType("WAIVER_PERIOD_PROCESSED");
  }

  public record WaiverStatus(boolean open, String reason, int processed) {
  }

  /** One fresh snapshot for API settings; no cross-request cache or stale bids. */
  public WaiverStatus waiverStatus(LeagueSettings league) {
    int processed = waiverPeriodsProcessed();
    String reason;
    int week = league.getCurrentWeek();
    if (week < 4)
      reason = "Free agency opens in Week 4. The commissioner has the league set to Week " + week
          + ".";
    else if (week > 4)
      reason = "Free agency is only available in Week 4. The league is currently in Week " + week
          + ".";
    else if (league.isWaiverProcessed() || processed >= 2)
      reason = "Both Week 4 waiver periods have already been processed. Free agency is closed.";
    else {
      List<Matchup> third = matchups.findByWeekOrderByIdAsc(3);
      long completed = third.stream().filter(Matchup::isPlayed).count();
      if (third.size() != 8 || completed != 8)
        reason = "All 8 Week 3 game results must be recorded before Week 4 free agency opens ("
            + completed + "/8 complete).";
      else {
        LocalDateTime now = LocalDateTime.now(clock.withZone(EASTERN));
        reason = !now.isBefore(OPENS[processed]) && now.isBefore(CLOSES[processed]) ? null
            : "FAAB Period " + (processed + 1) + " accepts bids only from " + OPENS[processed]
                + " through before " + CLOSES[processed]
                + " Eastern (7:00 PM cutoff). Current Eastern time: " + now + ".";
      }
    }
    return new WaiverStatus(reason == null, reason, processed);
  }

  public boolean isWaiverWindowOpen() {
    return waiverStatus(settings.findById(1L).orElseGet(LeagueSettings::new)).open();
  }

  public String waiverClosedReason() {
    return waiverStatus(settings.findById(1L).orElseGet(LeagueSettings::new)).reason();
  }

  @Transactional
  public FreeAgentBid submitBid(Long teamId, Long wanted, Long drop, int amount) {
    LeagueSettings league = settings.lockForDraft()
        .orElseGet(() -> settings.findById(1L).orElseThrow());
    String reason = waiverStatus(league).reason();
    if (reason != null)
      throw bad(reason);
    Team t = teams.findById(teamId).orElseThrow();
    if (t.getFaBudget() <= 0)
      throw bad("You have $0 FAAB remaining. You cannot submit another bid.");
    if (amount <= 0 || amount > 3 || amount > t.getFaBudget())
      throw bad("Enter a whole-dollar bid between $1 and $" + Math.min(3, t.getFaBudget()) + ".");
    List<FreeAgentBid> pending = bids.findByTeamIdAndStatusOrderByCreatedAtDesc(teamId,
        BidStatus.PENDING);
    if (pending.size() >= 3)
      throw bad("You already have 3 pending FAAB bids, the maximum allowed.");
    int reserved = pending.stream().mapToInt(FreeAgentBid::getAmount).sum();
    int available = Math.min(3, t.getFaBudget()) - reserved;
    if (amount > available)
      throw bad("You have $" + available + " FAAB available after pending bids. Reduce your bid.");
    if (pending.stream().anyMatch(b -> Objects.equals(b.getWantedPokemonId(), wanted)))
      throw bad("You already have a pending bid for this Pokémon. Choose another free agent.");
    if (pending.stream().anyMatch(b -> Objects.equals(b.getDropPokemonId(), drop)))
      throw bad("This Pokémon is already selected as the drop in another pending bid.");
    if (wanted == null)
      throw bad("Choose the free-agent Pokémon you want to acquire.");
    Pokemon w = pokemon.findById(wanted)
        .orElseThrow(() -> bad("This free agent no longer exists. Refresh the list."));
    if (roster.findByPokemonId(wanted).isPresent() || w.isDrafted())
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          w.getName() + " is already owned by a team and is not available as a free agent.");
    int count = (int) roster.countByTeamId(teamId);
    if (count != ROSTER_LIMIT)
      throw bad(
          "Your roster has " + count + "/10 Pokémon. You need exactly 10 to submit a FAAB bid.");
    if (drop == null)
      throw bad("Choose a Pokémon from your roster to drop if your bid wins.");
    RosterEntry d = verifyOwner(teamId, drop);
    Pokemon dp = pokemon.findById(d.getPokemonId()).orElseThrow();
    if (w.getPrice() > dp.getPrice())
      throw bad(w.getName() + " costs $" + w.getPrice() + ", but " + dp.getName() + " costs $"
          + dp.getPrice() + ". The free agent cannot cost more than your drop.");
    int value = rosterValue(teamId) - dp.getPrice() + w.getPrice();
    if (value > ROSTER_BUDGET)
      throw bad("This swap would put your roster at $" + value + "/$100. The cap is $100.");
    return bids.save(new FreeAgentBid(teamId, wanted, drop, amount));
  }

  /** Withdraw only your own pending bid before processing begins. */
  @Transactional
  public void cancelBid(Long teamId, Long bidId) {
    LeagueSettings league = settings.lockForDraft()
        .orElseGet(() -> settings.findById(1L).orElseThrow());
    String reason = waiverStatus(league).reason();
    if (reason != null)
      throw bad(reason);
    FreeAgentBid bid = bids.findById(bidId).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That FAAB bid no longer exists."));
    if (!bid.getTeamId().equals(teamId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "You can only withdraw bids made by your own team.");
    if (bid.getStatus() != BidStatus.PENDING)
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "This bid has already been processed and cannot be withdrawn.");
    bids.delete(bid);
  }

  @Transactional
  public Map<String, Object> processBids() {
    LeagueSettings league = settings.lockForDraft()
        .orElseGet(() -> settings.findById(1L).orElseThrow());
    int processed = waiverPeriodsProcessed();
    // Commissioner processes only after bidding closes; allow overdue processing
    // so missing the end-of-day target cannot permanently strand a period.
    if (waiverClosedForProcessing(league, processed))
      throw bad("FAAB processing is only available after 7:00 PM Eastern on "
          + (processed == 0 ? "November 28" : "December 1") + ".");
    List<FreeAgentBid> pending = bids.findByStatus(BidStatus.PENDING);
    Map<Long, List<FreeAgentBid>> groups = new LinkedHashMap<>();
    for (FreeAgentBid bid : pending)
      groups.computeIfAbsent(bid.getWantedPokemonId(), k -> new ArrayList<>()).add(bid);
    List<List<FreeAgentBid>> ordered = new ArrayList<>(groups.values());
    ordered.sort(Comparator.comparingInt((List<FreeAgentBid> group) -> group.stream()
        .mapToInt(FreeAgentBid::getAmount).max().orElse(0)).reversed());
    List<Map<String, Object>> wheels = new ArrayList<>();
    int winners = 0;
    for (List<FreeAgentBid> group : ordered) {
      List<FreeAgentBid> candidates = new ArrayList<>(group);
      FreeAgentBid winner = null;
      while (!candidates.isEmpty()) {
        int top = candidates.stream().mapToInt(FreeAgentBid::getAmount).max().orElse(0);
        List<FreeAgentBid> valid = candidates.stream()
            .filter(bid -> bid.getAmount() == top && canApplyBid(bid)).toList();
        if (valid.isEmpty()) {
          candidates.removeIf(bid -> bid.getAmount() == top);
          continue;
        }
        int fewestWins = valid.stream()
            .mapToInt(bid -> teams.findById(bid.getTeamId()).orElseThrow().getWins()).min()
            .orElse(0);
        List<FreeAgentBid> worse = valid.stream()
            .filter(bid -> teams.findById(bid.getTeamId()).orElseThrow().getWins() == fewestWins)
            .toList();
        int mostLosses = worse.stream()
            .mapToInt(bid -> teams.findById(bid.getTeamId()).orElseThrow().getLosses()).max()
            .orElse(0);
        List<FreeAgentBid> tied = worse.stream()
            .filter(bid -> teams.findById(bid.getTeamId()).orElseThrow().getLosses() == mostLosses)
            .toList();
        winner = tied.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(tied.size()));
        if (tied.size() > 1) {
          String name = pokemon.findById(winner.getWantedPokemonId()).orElseThrow().getName();
          List<String> names = tied.stream()
              .map(bid -> teams.findById(bid.getTeamId()).orElseThrow().getName()).toList();
          String winningName = teams.findById(winner.getTeamId()).orElseThrow().getName();
          wheels.add(Map.of("pokemon", name, "teams", names, "winner", winningName));
          transactions.save(new TransactionRecord("WAIVER_TIEBREAK", "Wheel draw for " + name + ": "
              + String.join(", ", names) + ". Winner: " + winningName));
        }
        break;
      }
      for (FreeAgentBid bid : group)
        bid.setStatus(bid == winner ? BidStatus.WON : BidStatus.LOST);
      if (winner != null) {
        applyWinningBid(winner);
        winners++;
      }
      bids.saveAll(group);
    }
    int period = processed + 1;
    transactions.save(new TransactionRecord("WAIVER_PERIOD_PROCESSED",
        "Week 4 waiver period " + period + " processed. Winners: " + winners));
    league.setWaiverProcessed(period >= 2);
    settings.save(league);
    return Map.of("winners", winners, "wheels", wheels, "period", period);
  }

  private boolean waiverClosedForProcessing(LeagueSettings league, int period) {
    if (league.getCurrentWeek() != 4 || league.isWaiverProcessed() || period >= 2)
      return true;
    List<Matchup> games = matchups.findByWeekOrderByIdAsc(3);
    return games.size() != 8 || games.stream().anyMatch(m -> !m.isPlayed())
        || LocalDateTime.now(clock.withZone(EASTERN)).isBefore(CLOSES[period]);
  }

  private boolean canApplyBid(FreeAgentBid b) {
    Team t = teams.findById(b.getTeamId()).orElse(null);
    Pokemon w = pokemon.findById(b.getWantedPokemonId()).orElse(null);
    if (t == null || w == null || b.getAmount() <= 0 || b.getAmount() > 3
        || b.getAmount() > t.getFaBudget()
        || roster.findByPokemonId(b.getWantedPokemonId()).isPresent() || w.isDrafted())
      return false;
    if (roster.countByTeamId(b.getTeamId()) != ROSTER_LIMIT || b.getDropPokemonId() == null)
      return false;
    Optional<RosterEntry> d = roster.findByPokemonId(b.getDropPokemonId());
    if (d.isEmpty() || !d.get().getTeamId().equals(b.getTeamId()))
      return false;
    Pokemon dp = pokemon.findById(b.getDropPokemonId()).orElse(null);
    return dp != null && w.getPrice() <= dp.getPrice()
        && rosterValue(b.getTeamId()) - dp.getPrice() + w.getPrice() <= ROSTER_BUDGET;
  }

  private void applyWinningBid(FreeAgentBid b) {
    Team t = teams.findById(b.getTeamId()).orElseThrow();
    Pokemon w = pokemon.findById(b.getWantedPokemonId()).orElseThrow();
    Pokemon dp = pokemon.findById(b.getDropPokemonId()).orElseThrow();
    RosterEntry de = verifyOwner(t.getId(), dp.getId());
    int value = rosterValue(t.getId()) - dp.getPrice() + w.getPrice();
    if (w.getPrice() > dp.getPrice())
      throw bad(w.getName() + " ($" + w.getPrice() + ") costs more than " + dp.getName() + " ($"
          + dp.getPrice() + ").");
    if (value > ROSTER_BUDGET)
      throw bad("This winning bid would exceed the $100 roster cap (new total: $" + value + ").");
    roster.delete(de);
    dp.setDrafted(false);
    pokemon.save(dp);
    roster.save(new RosterEntry(t.getId(), w.getId()));
    w.setDrafted(true);
    pokemon.save(w);
    t.setFaBudget(t.getFaBudget() - b.getAmount());
    teams.save(t);
    transactions.save(new TransactionRecord("FREE_AGENCY",
        t.getName() + " added " + w.getName() + " for $" + b.getAmount() + " FAAB and dropped "
            + dp.getName() + ". Roster value: $" + value + "/$100; FAAB remaining: $"
            + t.getFaBudget()));
    cancelImpossibleTrades(Set.of(w.getId(), dp.getId()));
  }

  public TradeOffer offerTrade(Long teamId, Long recipient, List<Long> offered,
      List<Long> requested, int proposerFaab, int recipientFaab) {
    if (recipient == null)
      throw bad("Choose the team you want to trade with.");
    if (teamId.equals(recipient))
      throw bad("You cannot trade with your own team. Choose another coach.");
    offered = clean(offered);
    requested = clean(requested);
    if (offered.isEmpty() && requested.isEmpty() && proposerFaab == 0 && recipientFaab == 0)
      throw bad("A trade offer must include at least one Pokémon or some FAAB.");
    if (proposerFaab < 0 || recipientFaab < 0)
      throw bad("Trade FAAB amounts cannot be negative.");
    Team p = teams.findById(teamId).orElseThrow(), r = teams.findById(recipient).orElseThrow();
    if (proposerFaab > p.getFaBudget())
      throw bad(
          "You only have $" + p.getFaBudget() + " FAAB, so you cannot offer $" + proposerFaab);
    if (recipientFaab > r.getFaBudget())
      throw bad(r.getName() + " only has $" + r.getFaBudget() + " FAAB, so you cannot request $"
          + recipientFaab);
    for (Long id : offered)
      verifyOwner(teamId, id);
    for (Long id : requested)
      verifyOwner(recipient, id);
    validateTrade(teamId, recipient, offered, requested);
    return trades
        .save(new TradeOffer(teamId, recipient, offered, requested, proposerFaab, recipientFaab));
  }

  public TradeOffer offerTrade(Long teamId, Long recipient, Long offered, Long requested, int pf,
      int rf) {
    return offerTrade(teamId, recipient, offered == null ? List.of() : List.of(offered),
        requested == null ? List.of() : List.of(requested), pf, rf);
  }

  public TradeOffer offerTrade(Long teamId, Long recipient, Long offered, Long requested) {
    return offerTrade(teamId, recipient, offered, requested, 0, 0);
  }

  @Transactional
  public TradeOffer respondTrade(Long id, Long actingTeamId, boolean manager, boolean accept) {
    TradeOffer tr = trades.findById(id).orElseThrow(() -> bad("Trade offer no longer exists"));
    if (tr.getStatus() != TradeStatus.PENDING)
      throw bad("This trade is already " + tr.getStatus().name().toLowerCase()
          + " and can no longer be changed");
    if (!manager && !tr.getRecipientTeamId().equals(actingTeamId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Only the team receiving this trade can accept or reject it");
    if (!accept) {
      tr.setStatus(TradeStatus.REJECTED);
      return trades.save(tr);
    }
    List<Long> offered = tr.getOfferedPokemonIds(), requested = tr.getRequestedPokemonIds();
    if (!assetsStillOwned(tr.getProposerTeamId(), offered)
        || !assetsStillOwned(tr.getRecipientTeamId(), requested)) {
      tr.setStatus(TradeStatus.CANCELLED);
      return trades.save(tr);
    }
    int[] values = validateTrade(tr.getProposerTeamId(), tr.getRecipientTeamId(), offered,
        requested);
    Team p = teams.findById(tr.getProposerTeamId()).orElseThrow(),
        r = teams.findById(tr.getRecipientTeamId()).orElseThrow();
    if (tr.getProposerFaab() > p.getFaBudget() || tr.getRecipientFaab() > r.getFaBudget()) {
      tr.setStatus(TradeStatus.CANCELLED);
      return trades.save(tr);
    }
    for (Long x : offered) {
      RosterEntry e = verifyOwner(p.getId(), x);
      e.setTeamId(r.getId());
      roster.save(e);
    }
    for (Long x : requested) {
      RosterEntry e = verifyOwner(r.getId(), x);
      e.setTeamId(p.getId());
      roster.save(e);
    }
    p.setFaBudget(p.getFaBudget() - tr.getProposerFaab() + tr.getRecipientFaab());
    r.setFaBudget(r.getFaBudget() - tr.getRecipientFaab() + tr.getProposerFaab());
    teams.save(p);
    teams.save(r);
    tr.setStatus(TradeStatus.ACCEPTED);
    trades.save(tr);
    Set<Long> moved = new HashSet<>(offered);
    moved.addAll(requested);
    cancelImpossibleTradesExcept(moved, tr.getId());
    String desc = p.getName() + " traded " + describe(offered, tr.getProposerFaab()) + " to "
        + r.getName() + " for " + describe(requested, tr.getRecipientFaab());
    transactions
        .save(new TransactionRecord("TRADE", desc + ". Roster values: $" + values[0] + "/$100 and $"
            + values[1] + "/$100. FAAB remaining: $" + p.getFaBudget() + " / $" + r.getFaBudget()));
    return tr;
  }

  private int[] validateTrade(Long p, Long r, List<Long> offered, List<Long> requested) {
    Team pt = teams.findById(p).orElseThrow(), rt = teams.findById(r).orElseThrow();
    int pc = (int) roster.countByTeamId(p) - offered.size() + requested.size(),
        rc = (int) roster.countByTeamId(r) - requested.size() + offered.size();
    if (pc != ROSTER_LIMIT)
      throw bad("This trade would leave " + pt.getName() + " with " + pc
          + " Pokemon. Every roster must have exactly 10");
    if (rc != ROSTER_LIMIT)
      throw bad("This trade would leave " + rt.getName() + " with " + rc
          + " Pokemon. Every roster must have exactly 10");
    int pv = rosterValue(p) - value(offered) + value(requested),
        rv = rosterValue(r) - value(requested) + value(offered);
    if (pv > ROSTER_BUDGET)
      throw bad("This trade would put " + pt.getName() + " over the $100 roster budget at $" + pv
          + "/$100");
    if (rv > ROSTER_BUDGET)
      throw bad("This trade would put " + rt.getName() + " over the $100 roster budget at $" + rv
          + "/$100");
    return new int[] { pv, rv };
  }

  @Transactional
  public void cancelImpossibleTrades(Set<Long> moved) {
    cancelImpossibleTradesExcept(moved, null);
  }

  private void cancelImpossibleTradesExcept(Set<Long> moved, Long acceptedId) {
    for (TradeOffer t : trades.findByStatus(TradeStatus.PENDING)) {
      if (t.getStatus() != TradeStatus.PENDING || Objects.equals(t.getId(), acceptedId))
        continue;
      boolean conflict = t.getOfferedPokemonIds().stream().anyMatch(moved::contains)
          || t.getRequestedPokemonIds().stream().anyMatch(moved::contains);
      if (conflict) {
        t.setStatus(TradeStatus.CANCELLED);
        trades.save(t);
      }
    }
  }

  private boolean assetsStillOwned(Long teamId, List<Long> ids) {
    for (Long id : ids) {
      Optional<RosterEntry> e = roster.findByPokemonId(id);
      if (e.isEmpty() || !e.get().getTeamId().equals(teamId))
        return false;
    }
    return true;
  }

  private List<Long> clean(List<Long> ids) {
    return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
  }

  private int value(List<Long> ids) {
    return ids.isEmpty() ? 0 : Math.toIntExact(pokemon.totalPriceByIdIn(ids));
  }

  private String describe(List<Long> ids, int faab) {
    List<String> parts = new ArrayList<>();
    Map<Long, String> names = new HashMap<>();
    if (!ids.isEmpty())
      for (PokemonRepository.Name p : pokemon.findNamesByIdIn(ids))
        names.put(p.getId(), p.getName());
    for (Long id : ids)
      parts.add(names.getOrDefault(id, "Pokemon"));
    if (faab > 0)
      parts.add("$" + faab + " FAAB");
    return parts.isEmpty() ? "nothing" : String.join(" + ", parts);
  }

  private int rosterValue(Long teamId) {
    return Math.toIntExact(roster.totalPriceByTeamId(teamId));
  }

  private RosterEntry verifyOwner(Long teamId, Long pokemonId) {
    RosterEntry e = roster.findByPokemonId(pokemonId).orElseThrow(
        () -> bad(pokemonName(pokemonId) + " is no longer rostered. Refresh and try again."));
    if (!e.getTeamId().equals(teamId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, pokemonName(pokemonId)
          + " belongs to another team. You can only drop or trade Pokémon you own.");
    return e;
  }

  private String pokemonName(Long id) {
    return pokemon.findById(id).map(Pokemon::getName).orElse("That Pokemon");
  }

  private ResponseStatusException bad(String s) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
  }
}
