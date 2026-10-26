package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreeAgentBidRepository extends JpaRepository<FreeAgentBid, Long> {
  List<FreeAgentBid> findByTeamIdOrderByCreatedAtDesc(Long teamId);

  List<FreeAgentBid> findByStatus(BidStatus status);

  List<FreeAgentBid> findByTeamIdAndStatusOrderByCreatedAtDesc(Long teamId, BidStatus status);

  interface WonNotice {
    Long getId();

    int getAmount();

    String getPokemonName();
  }

  @org.springframework.data.jpa.repository.Query("select b.id as id, b.amount as amount, p.name as pokemonName from FreeAgentBid b left join"
      + " Pokemon p on p.id = b.wantedPokemonId where b.teamId = :teamId and b.status = :status"
      + " order by b.createdAt desc")
  List<WonNotice> findWonNotices(Long teamId, BidStatus status);
}
