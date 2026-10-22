package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.Matchup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchupRepository extends JpaRepository<Matchup, Long> {
  List<Matchup> findAllByOrderByWeekAscIdAsc();

  long countByPlayedTrue();

  List<Matchup> findByWeekOrderByIdAsc(int week);

  @org.springframework.data.jpa.repository.Query(
      "select m from Matchup m where m.week = :week and (m.homeTeamId = :teamId or m.awayTeamId ="
          + " :teamId) order by m.id")
  List<Matchup> findForTeamAndWeek(Long teamId, int week);

  @org.springframework.data.jpa.repository.Query(
      "select m from Matchup m where m.week >= 1 and (:week is null or m.week = :week) and (:teamId"
          + " is null or m.homeTeamId = :teamId or m.awayTeamId = :teamId) order by m.week, m.id")
  List<Matchup> findPublished(Integer week, Long teamId);
}
