package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.Team;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
  List<Team> findByDivision(String division);
}
