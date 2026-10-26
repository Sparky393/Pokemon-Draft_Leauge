package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class FreeAgentBid {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long teamId;
  private Long wantedPokemonId;
  private Long dropPokemonId;
  private int amount;

  @Enumerated(EnumType.STRING)
  private BidStatus status = BidStatus.PENDING;

  private LocalDateTime createdAt = LocalDateTime.now();

  public FreeAgentBid() {
  }

  public FreeAgentBid(Long teamId, Long wanted, Long drop, int amount) {
    this.teamId = teamId;
    this.wantedPokemonId = wanted;
    this.dropPokemonId = drop;
    this.amount = amount;
  }

  public Long getId() {
    return id;
  }

  public Long getTeamId() {
    return teamId;
  }

  public Long getWantedPokemonId() {
    return wantedPokemonId;
  }

  public Long getDropPokemonId() {
    return dropPokemonId;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int v) {
    amount = v;
  }

  public BidStatus getStatus() {
    return status;
  }

  public void setStatus(BidStatus v) {
    status = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
