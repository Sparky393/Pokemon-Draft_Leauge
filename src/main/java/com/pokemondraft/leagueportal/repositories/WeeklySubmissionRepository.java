package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.WeeklySubmission;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklySubmissionRepository extends JpaRepository<WeeklySubmission, Long> {
  Optional<WeeklySubmission> findByTeamIdAndWeek(Long teamId, int week);

  List<WeeklySubmission> findByWeek(int week);

  boolean existsByTeamIdAndWeek(Long teamId, int week);

  @org.springframework.data.jpa.repository.Query(
      "select s.teamId from WeeklySubmission s where s.week = :week")
  List<Long> findSubmittedTeamIds(int week);

  @org.springframework.data.jpa.repository.Query(
      "select s from WeeklySubmission s where exists (select m.id from Matchup m where m.id in"
          + " :gameIds and m.week = s.week and (m.homeTeamId = s.teamId or m.awayTeamId ="
          + " s.teamId))")
  List<WeeklySubmission> findForMatchups(Collection<Long> gameIds);

  Optional<WeeklySubmission> findFirstByTeamIdAndWeekLessThanOrderByWeekDesc(Long teamId, int week);

  @org.springframework.data.jpa.repository.Query(
      "select s from WeeklySubmission s where s.teamId in :teamIds and s.week < :week and s.week ="
          + " (select max(previous.week) from WeeklySubmission previous where previous.teamId ="
          + " s.teamId and previous.week < :week)")
  List<WeeklySubmission> findLatestBeforeWeek(Collection<Long> teamIds, int week);
}
