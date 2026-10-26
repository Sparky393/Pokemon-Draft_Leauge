package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "teamId", "week" }))
public class WeeklySubmission {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long teamId;
  private int week;

  @Column(length = 1000)
  private String pokemonIdsCsv;

  private LocalDateTime submittedAt;
  private boolean automatic = false;

  public WeeklySubmission() {
  }

  public WeeklySubmission(Long teamId, int week, String csv) {
    this.teamId = teamId;
    this.week = week;
    this.pokemonIdsCsv = csv;
    this.submittedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Long getTeamId() {
    return teamId;
  }

  public int getWeek() {
    return week;
  }

  public String getPokemonIdsCsv() {
    return pokemonIdsCsv;
  }

  public void setPokemonIdsCsv(String v) {
    pokemonIdsCsv = v;
  }

  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(LocalDateTime v) {
    submittedAt = v;
  }

  public boolean isAutomatic() {
    return automatic;
  }

  public void setAutomatic(boolean v) {
    automatic = v;
  }
}
