package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.DraftPick;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftPickRepository extends JpaRepository<DraftPick, Long> {
  List<DraftPick> findAllByOrderByOverallPickAsc();

  java.util.Optional<DraftPick> findFirstByOrderByOverallPickDesc();

  interface Status {
    long getPickCount();

    Long getLastPickId();
  }

  @org.springframework.data.jpa.repository.Query(
      "select count(p) as pickCount, max(p.id) as lastPickId from DraftPick p")
  Status status();
}
