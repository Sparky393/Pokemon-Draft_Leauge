package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.TradeOffer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOfferRepository extends JpaRepository<TradeOffer, Long> {
  List<TradeOffer> findByProposerTeamIdOrRecipientTeamIdOrderByCreatedAtDesc(
      Long proposer, Long recipient);
}
