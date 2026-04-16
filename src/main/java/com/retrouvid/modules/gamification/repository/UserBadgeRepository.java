package com.retrouvid.modules.gamification.repository;

import com.retrouvid.modules.gamification.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

    List<UserBadge> findByUserId(UUID userId);

    Optional<UserBadge> findByUserIdAndBadgeIdAndMetadataKey(
            UUID userId, UUID badgeId, String metadataKey);
}
