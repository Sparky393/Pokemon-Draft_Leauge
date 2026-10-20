package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.DraftPick;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftPickRepository extends JpaRepository<DraftPick, Long> {
  List<DraftPick> findAllByOrderByOverallPickAsc();
}
