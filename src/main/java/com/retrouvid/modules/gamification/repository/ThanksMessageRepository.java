package com.retrouvid.modules.gamification.repository;

import com.retrouvid.modules.gamification.entity.ThanksMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ThanksMessageRepository extends JpaRepository<ThanksMessage, UUID> {

    Optional<ThanksMessage> findByMatchId(UUID matchId);

    Page<ThanksMessage> findByToUserIdOrderByCreatedAtDesc(UUID toUserId, Pageable pageable);
}
