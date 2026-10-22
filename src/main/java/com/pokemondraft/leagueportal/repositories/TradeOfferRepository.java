package com.pokemondraft.leagueportal.repositories;

import com.pokemondraft.leagueportal.entity.TradeOffer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOfferRepository extends JpaRepository<TradeOffer, Long> {
  List<TradeOffer> findByStatus(com.pokemondraft.leagueportal.entity.TradeStatus status);

  List<TradeOffer> findByProposerTeamIdOrRecipientTeamIdOrderByCreatedAtDesc(
      Long proposer, Long recipient);
}
