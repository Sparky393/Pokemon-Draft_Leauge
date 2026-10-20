package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.Pokemon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokemonRepository extends JpaRepository<Pokemon, Long> {
  List<Pokemon> findAllByOrderByNameAsc();

  List<Pokemon> findByDraftedFalseOrderByNameAsc();

  Optional<Pokemon> findByName(String name);
}
