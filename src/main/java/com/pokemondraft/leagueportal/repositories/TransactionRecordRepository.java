package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.TransactionRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {
  List<TransactionRecord> findAllByOrderByCreatedAtDesc();
  long countByType(String type);
}
