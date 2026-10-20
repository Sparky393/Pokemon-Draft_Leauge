package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreeAgentBidRepository extends JpaRepository<FreeAgentBid, Long> {
  List<FreeAgentBid> findByTeamIdOrderByCreatedAtDesc(Long teamId);

  List<FreeAgentBid> findByStatus(BidStatus status);

  List<FreeAgentBid> findByTeamIdAndStatusOrderByCreatedAtDesc(Long teamId, BidStatus status);
}
