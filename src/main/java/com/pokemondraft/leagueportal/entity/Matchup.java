package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.*;

@Entity
public class Matchup {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private int week;
  private Long homeTeamId;
  private Long awayTeamId;
  private Integer homeScore;
  private Integer awayScore;
  private boolean played;

  public Matchup() {
  }

  public Matchup(int week, Long homeTeamId, Long awayTeamId) {
    this.week = week;
    this.homeTeamId = homeTeamId;
    this.awayTeamId = awayTeamId;
  }

  public Long getId() {
    return id;
  }

  public int getWeek() {
    return week;
  }

  public void setWeek(int v) {
    week = v;
  }

  public Long getHomeTeamId() {
    return homeTeamId;
  }

  public void setHomeTeamId(Long v) {
    homeTeamId = v;
  }

  public Long getAwayTeamId() {
    return awayTeamId;
  }

  public void setAwayTeamId(Long v) {
    awayTeamId = v;
  }

  public Integer getHomeScore() {
    return homeScore;
  }

  public void setHomeScore(Integer v) {
    homeScore = v;
  }

  public Integer getAwayScore() {
    return awayScore;
  }

  public void setAwayScore(Integer v) {
    awayScore = v;
  }

  public boolean isPlayed() {
    return played;
  }

  public void setPlayed(boolean v) {
    played = v;
  }
}
