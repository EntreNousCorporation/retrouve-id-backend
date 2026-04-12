package com.retrouvid.modules.messaging.repository;

import com.retrouvid.modules.messaging.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByMatchId(UUID matchId);

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.user1.id = :userId OR c.user2.id = :userId
        ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC
    """)
    Page<Conversation> findForUser(@Param("userId") UUID userId, Pageable pageable);
}
