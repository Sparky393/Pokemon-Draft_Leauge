package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pokemon_pool")
public class Pokemon {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String name;

  private int price;
  private boolean drafted;

  public Pokemon() {}

  public Pokemon(String name, int price) {
    this.name = name;
    this.price = price;
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

  public int getPrice() {
    return price;
  }

  public void setPrice(int v) {
    price = v;
  }

  public boolean isDrafted() {
    return drafted;
  }

  public void setDrafted(boolean v) {
    drafted = v;
  }
}
