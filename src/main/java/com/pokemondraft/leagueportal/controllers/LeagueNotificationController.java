package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.*;
import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/**
 * Non-destructive notifications derived from existing league records.
 * No new notification table, mail service, or production database mutation.
 */
@RestController
@RequestMapping("/api/notifications")
public class LeagueNotificationController {
  private final AuthService auth;
  private final DraftService draft;
  private final DraftPickRepository picks;
  private final LeagueSettingsRepository settings;
  private final WeeklySubmissionRepository submissions;
  private final MatchupRepository matchups;
  private final FreeAgentBidRepository bids;
  private final PokemonRepository pokemon;
  private final TeamRepository teams;
  private final CommissionerReminderController commissionerReminders;

  public LeagueNotificationController(AuthService auth, DraftService draft,
      DraftPickRepository picks, LeagueSettingsRepository settings,
      WeeklySubmissionRepository submissions, MatchupRepository matchups,
      FreeAgentBidRepository bids, PokemonRepository pokemon, TeamRepository teams,
      CommissionerReminderController commissionerReminders) {
    this.auth=auth; this.draft=draft; this.picks=picks; this.settings=settings;
    this.submissions=submissions; this.matchups=matchups; this.bids=bids;
    this.pokemon=pokemon; this.teams=teams;
    this.commissionerReminders=commissionerReminders;
  }

  public record Notice(String id, String title, String message, String page) {}

  @GetMapping
  public List<Notice> myNotifications(HttpSession session) {
    AppUser user=auth.current(session);
    if (user.getRole()==Role.MANAGER) {
      return commissionerReminders.remindersAt(LocalDate.now(ZoneId.of("America/New_York")))
          .stream().map(n -> new Notice(n.id(),n.title(),n.message(),"manager")).toList();
    }
    if (user.getRole()!=Role.COACH || user.getTeamId()==null) return List.of();
    Long teamId=user.getTeamId();
    LeagueSettings league=settings.findById(1L).orElseGet(LeagueSettings::new);
    List<Notice> out=new ArrayList<>();

    if (league.isDraftOpen() && !league.isDraftFinalized()) {
      Team onClock=draft.currentTeam();
      if (onClock!=null && teamId.equals(onClock.getId())) {
        long selection=picks.count()+1;
        out.add(new Notice("draft:"+selection, "You're on the clock!",
            "It's your turn for overall pick #"+selection+". Open the Draft Room to select a Pokémon.",
            "draft"));
      }
    }

    int week=league.getCurrentWeek();
    if (SeasonCalendar.week(week).lineupRequired()
        && matchups.findByWeekOrderByIdAsc(week).stream()
            .anyMatch(m -> teamId.equals(m.getHomeTeamId()) || teamId.equals(m.getAwayTeamId()))
        && submissions.findByTeamIdAndWeek(teamId,week).isEmpty()
        && league.getLineupDeadline()!=null) {
      // League deadlines are entered in the commissioner's Eastern local time.
      LocalDateTime now=LocalDateTime.now(ZoneId.of("America/New_York"));
      Duration left=Duration.between(now,league.getLineupDeadline());
      if (!left.isNegative() && left.compareTo(Duration.ofHours(24))<=0)
        out.add(new Notice("lineup:"+week, "Your Week "+week+" lineup is due soon",
            "Submit your six Pokémon before "+league.getLineupDeadline().toString().replace('T',' ')
                +" Eastern.", "lineup"));
    }

    for (FreeAgentBid bid : bids.findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, BidStatus.WON)) {
      if (bid.getStatus()!=BidStatus.WON) continue;
      Pokemon p=pokemon.findById(bid.getWantedPokemonId()).orElse(null);
      String name=p==null?"your selected Pokémon":p.getName();
      out.add(new Notice("faab:"+bid.getId(), "FAAB bid won!",
          "Your $"+bid.getAmount()+" FAAB bid for "+name+" was successful. Check your roster.",
          "freeagency"));
    }
    return out;
  }
}
