package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.LeagueSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface LeagueSettingsRepository extends JpaRepository<LeagueSettings, Long> {
  /** Serializes draft picks across concurrent Render workers. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from LeagueSettings s where s.id = 1")
  Optional<LeagueSettings> lockForDraft();
}
