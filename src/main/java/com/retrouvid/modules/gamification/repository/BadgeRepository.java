package com.retrouvid.modules.gamification.repository;

import com.retrouvid.modules.gamification.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {
    Optional<Badge> findByCode(String code);
}
