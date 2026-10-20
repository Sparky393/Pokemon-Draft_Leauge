package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class DraftPick {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private int overallPick;
  private int roundNumber;
  private Long teamId;
  private Long pokemonId;
  private LocalDateTime pickedAt;

  public DraftPick() {}

  public DraftPick(int overallPick, int roundNumber, Long teamId, Long pokemonId) {
    this.overallPick = overallPick;
    this.roundNumber = roundNumber;
    this.teamId = teamId;
    this.pokemonId = pokemonId;
    this.pickedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public int getOverallPick() {
    return overallPick;
  }

  public int getRoundNumber() {
    return roundNumber;
  }

  public Long getTeamId() {
    return teamId;
  }

  public Long getPokemonId() {
    return pokemonId;
  }

  public LocalDateTime getPickedAt() {
    return pickedAt;
  }
}
