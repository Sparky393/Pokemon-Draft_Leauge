package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "pokemonId"))
public class RosterEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long teamId;
  private Long pokemonId;

  public RosterEntry() {}

  public RosterEntry(Long teamId, Long pokemonId) {
    this.teamId = teamId;
    this.pokemonId = pokemonId;
  }

  public Long getId() {
    return id;
  }

  public Long getTeamId() {
    return teamId;
  }

  public void setTeamId(Long v) {
    teamId = v;
  }

  public Long getPokemonId() {
    return pokemonId;
  }

  public void setPokemonId(Long v) {
    pokemonId = v;
  }
}
