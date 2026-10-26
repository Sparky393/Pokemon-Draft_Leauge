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

  String METADATA = "select new com.pokemondraft.leagueportal.entity.Team(t.id, t.name, t.coachName, t.division,"
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
  @Query("update Team t set t.logoBase64 = :image, t.logoContentType = :contentType, t.logoVersion ="
      + " :version where t.id = :id")
  int updateLogo(Long id, String image, String contentType, Long version);

  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying
  @Query("update Team t set t.wins = :wins, t.losses = :losses, t.differential = :differential where"
      + " t.id = :id")
  int updateStandings(Long id, int wins, int losses, int differential);

  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying
  @Query("update Team t set t.officialSlot = :slot where t.id = :id")
  int updateOfficialSlot(Long id, Integer slot);

  interface LogoMetadata {
    Long getLogoVersion();

    String getLogoContentType();

    boolean getPresent();
  }

  @Query("select t.logoVersion as logoVersion, t.logoContentType as logoContentType, case when"
      + " t.logoBase64 is not null then true else false end as present from Team t where t.id ="
      + " :id")
  Optional<LogoMetadata> findLogoMetadata(Long id);

  interface LogoImage {
    String getLogoBase64();

    String getLogoContentType();

    Long getLogoVersion();
  }

  @Query("select t.logoBase64 as logoBase64, t.logoContentType as logoContentType, t.logoVersion as"
      + " logoVersion from Team t where t.id = :id")
  Optional<LogoImage> findLogoImage(Long id);
}
