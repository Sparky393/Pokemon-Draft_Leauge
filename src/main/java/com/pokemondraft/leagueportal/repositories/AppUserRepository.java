package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByUsername(String username);

  @org.springframework.data.jpa.repository.Query(
      "select new com.pokemondraft.leagueportal.entity.AppUser(u.id, u.username, u.role, u.teamId)"
          + " from AppUser u where u.id = :id")
  Optional<AppUser> findIdentityById(Long id);

  @org.springframework.data.jpa.repository.Query(
      "select new com.pokemondraft.leagueportal.entity.AppUser(u.id, u.username, u.role, u.teamId)"
          + " from AppUser u where u.role <> com.pokemondraft.leagueportal.entity.Role.MANAGER")
  java.util.List<AppUser> findResettableIdentities();
}
