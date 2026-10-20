package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.*;

@Entity
public class Team {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String name;

  private String coachName;

  @Column(nullable = false)
  private String division;

  private int wins;
  private int losses;
  private int differential;
  private int faBudget = 3;
  private int draftPosition;
  private int manualRank;

  // Persist logo bytes in Neon. The public Team JSON includes only the image URL.
  @Column(columnDefinition = "text")
  private String logoBase64;
  private String logoContentType;
  private Long logoVersion;
  // An immutable fixture-sheet identity: display names are editable by coaches.
  private Integer officialSlot;

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getLogoBase64() { return logoBase64; }
  public void setLogoBase64(String value) { logoBase64 = value; }
  public String getLogoContentType() { return logoContentType; }
  public void setLogoContentType(String value) { logoContentType = value; }
  public Long getLogoVersion() { return logoVersion; }
  public void setLogoVersion(Long value) { logoVersion = value; }
  public Integer getOfficialSlot() { return officialSlot; }
  public void setOfficialSlot(Integer value) { officialSlot = value; }
  public String getLogoUrl() {
    return logoBase64 == null || id == null ? null :
        "/api/teams/" + id + "/logo?v=" + (logoVersion == null ? 0 : logoVersion);
  }

  public Team() {}

  public Team(String name, String coachName, String division, int draftPosition) {
    this.name = name;
    this.coachName = coachName;
    this.division = division;
    this.draftPosition = draftPosition;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getCoachName() {
    return coachName;
  }

  public void setCoachName(String v) {
    coachName = v;
  }

  public String getDivision() {
    return division;
  }

  public void setDivision(String v) {
    division = v;
  }

  public int getWins() {
    return wins;
  }

  public void setWins(int v) {
    wins = v;
  }

  public int getLosses() {
    return losses;
  }

  public void setLosses(int v) {
    losses = v;
  }

  public int getDifferential() {
    return differential;
  }

  public void setDifferential(int v) {
    differential = v;
  }

  public int getFaBudget() {
    return faBudget;
  }

  public void setFaBudget(int v) {
    faBudget = v;
  }

  public int getDraftPosition() {
    return draftPosition;
  }

  public void setDraftPosition(int v) {
    draftPosition = v;
  }

  public int getManualRank() {
    return manualRank;
  }

  public void setManualRank(int v) {
    manualRank = v;
  }
}
