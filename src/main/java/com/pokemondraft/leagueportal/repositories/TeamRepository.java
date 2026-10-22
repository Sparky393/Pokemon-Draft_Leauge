package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamRepository extends JpaRepository<Team, Long> {
  List<Team> findByDivision(String division);

  interface DraftOrder {
    Long getId();

    int getDraftPosition();
  }

  List<DraftOrder> findAllProjectedByOrderByDraftPositionAsc();

  interface Name {
    Long getId();

    String getName();
  }

  List<Name> findAllProjectedBy();

  List<Name> findNamesByIdIn(java.util.Collection<Long> ids);

  boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

  String METADATA =
      "select new com.pokemondraft.leagueportal.entity.Team(t.id, t.name, t.coachName, t.division,"
          + " t.wins, t.losses, t.differential, t.faBudget, t.draftPosition, t.manualRank,"
          + " t.logoContentType, t.logoVersion, t.officialSlot, case when t.logoBase64 is not null"
          + " then true else false end) from Team t";

  @Query(METADATA)
  List<Team> findAllMetadata();

  @Query(METADATA + " where t.division = :division")
  List<Team> findMetadataByDivision(String division);

  @Query(METADATA + " where t.id = :id")
  Optional<Team> findMetadataById(Long id);

  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying
  @Query("update Team t set t.name = :name where t.id = :id")
  int rename(Long id, String name);

  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying
  @Query(
      "update Team t set t.logoBase64 = :image, t.logoContentType = :contentType, t.logoVersion ="
          + " :version where t.id = :id")
  int updateLogo(Long id, String image, String contentType, Long version);
}
