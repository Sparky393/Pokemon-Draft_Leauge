package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
public class TradeOffer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long proposerTeamId;
  private Long recipientTeamId;

  @Column(length = 2000)
  private String offeredPokemonIdsCsv = "";

  @Column(length = 2000)
  private String requestedPokemonIdsCsv = "";

  private int proposerFaab;
  private int recipientFaab;

  @Enumerated(EnumType.STRING)
  private TradeStatus status = TradeStatus.PENDING;

  private LocalDateTime createdAt = LocalDateTime.now();

  public TradeOffer() {}

  public TradeOffer(
      Long p,
      Long r,
      List<Long> offered,
      List<Long> requested,
      int proposerFaab,
      int recipientFaab) {
    this.proposerTeamId = p;
    this.recipientTeamId = r;
    setOfferedPokemonIds(offered);
    setRequestedPokemonIds(requested);
    this.proposerFaab = proposerFaab;
    this.recipientFaab = recipientFaab;
  }

  private String csv(List<Long> ids) {
    return ids == null
        ? ""
        : ids.stream()
            .filter(Objects::nonNull)
            .distinct()
            .map(String::valueOf)
            .reduce((a, b) -> a + "," + b)
            .orElse("");
  }

  private List<Long> ids(String csv) {
    if (csv == null || csv.isBlank()) return List.of();
    return Arrays.stream(csv.split(",")).filter(x -> !x.isBlank()).map(Long::valueOf).toList();
  }

  public Long getId() {
    return id;
  }

  public Long getProposerTeamId() {
    return proposerTeamId;
  }

  public Long getRecipientTeamId() {
    return recipientTeamId;
  }

  public List<Long> getOfferedPokemonIds() {
    return ids(offeredPokemonIdsCsv);
  }

  public void setOfferedPokemonIds(List<Long> v) {
    offeredPokemonIdsCsv = csv(v);
  }

  public List<Long> getRequestedPokemonIds() {
    return ids(requestedPokemonIdsCsv);
  }

  public void setRequestedPokemonIds(List<Long> v) {
    requestedPokemonIdsCsv = csv(v);
  }

  // Compatibility for old frontend/data consumers.
  public Long getOfferedPokemonId() {
    return getOfferedPokemonIds().stream().findFirst().orElse(null);
  }

  public Long getRequestedPokemonId() {
    return getRequestedPokemonIds().stream().findFirst().orElse(null);
  }

  public int getProposerFaab() {
    return proposerFaab;
  }

  public int getRecipientFaab() {
    return recipientFaab;
  }

  public TradeStatus getStatus() {
    return status;
  }

  public void setStatus(TradeStatus v) {
    status = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
