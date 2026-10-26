package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.Pokemon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokemonRepository extends JpaRepository<Pokemon, Long> {
  List<Pokemon> findAllByOrderByNameAsc();

  List<Pokemon> findByDraftedFalseOrderByNameAsc();

  Optional<Pokemon> findByName(String name);

  @org.springframework.data.jpa.repository.Query("select coalesce(sum(p.price), 0) from Pokemon p where p.id in :ids")
  long totalPriceByIdIn(java.util.Collection<Long> ids);

  interface Name {
    Long getId();

    String getName();
  }

  interface DraftDetails {
    Long getId();

    String getName();

    int getPrice();
  }

  List<DraftDetails> findDraftDetailsByIdIn(java.util.Collection<Long> ids);

  List<Name> findNamesByIdIn(java.util.Collection<Long> ids);
}
