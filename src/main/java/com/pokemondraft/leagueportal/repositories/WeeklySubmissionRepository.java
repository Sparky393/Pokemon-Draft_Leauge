package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.WeeklySubmission;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklySubmissionRepository extends JpaRepository<WeeklySubmission, Long> {
  Optional<WeeklySubmission> findByTeamIdAndWeek(Long teamId, int week);

  List<WeeklySubmission> findByWeek(int week);
}
