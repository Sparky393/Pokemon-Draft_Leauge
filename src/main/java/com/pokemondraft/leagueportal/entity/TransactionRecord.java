package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TransactionRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String type;

  @Column(length = 1000)
  private String description;

  private LocalDateTime createdAt = LocalDateTime.now();

  public TransactionRecord() {
  }

  public TransactionRecord(String type, String description) {
    this.type = type;
    this.description = description;
  }

  public Long getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
