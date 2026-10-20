package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.Matchup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchupRepository extends JpaRepository<Matchup, Long> {
  List<Matchup> findAllByOrderByWeekAscIdAsc();

  List<Matchup> findByWeekOrderByIdAsc(int week);
}
