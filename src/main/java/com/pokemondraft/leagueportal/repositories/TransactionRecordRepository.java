package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.TransactionRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {
  List<TransactionRecord> findAllByOrderByCreatedAtDesc();

  long countByType(String type);

  interface Revision {
    long getRowCount();

    Long getLatestId();
  }

  @org.springframework.data.jpa.repository.Query(
      "select count(t) as rowCount, max(t.id) as latestId from TransactionRecord t")
  Revision revision();
}
