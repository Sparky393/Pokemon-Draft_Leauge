package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.LeagueSettings;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LeagueSettingsRepository extends JpaRepository<LeagueSettings, Long> {
  interface DraftFlags {
    boolean isDraftOpen();

    boolean isDraftFinalized();

    boolean isSnakeDraft();
  }

  @Query(
      "select s.draftOpen as draftOpen, coalesce(s.draftFinalized, false) as draftFinalized,"
          + " s.snakeDraft as snakeDraft from LeagueSettings s where s.id = :id")
  Optional<DraftFlags> findDraftFlagsById(Long id);

  interface IdleStatus extends DraftFlags {
    long getPickCount();

    Long getLastPickId();
  }

  @Query(
      "select s.draftOpen as draftOpen, coalesce(s.draftFinalized, false) as draftFinalized,"
          + " s.snakeDraft as snakeDraft, (select count(p) from DraftPick p) as pickCount, (select"
          + " max(p.id) from DraftPick p) as lastPickId from LeagueSettings s where s.id = 1")
  Optional<IdleStatus> idleStatus();

  /** Serializes draft picks across concurrent Render workers. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from LeagueSettings s where s.id = 1")
  Optional<LeagueSettings> lockForDraft();
}
