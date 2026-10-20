package com.pokemondraft.leagueportal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class WeekLock {
  @Id
  private Integer week;
  private boolean locked;

  protected WeekLock() {}

  public WeekLock(int week, boolean locked) {
    this.week = week;
    this.locked = locked;
  }

  public Integer getWeek() { return week; }
  public boolean isLocked() { return locked; }
  public void setLocked(boolean value) { locked = value; }
}
