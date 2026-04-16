package com.retrouvid.modules.gamification.repository;

import com.retrouvid.modules.gamification.entity.UserStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {

    Optional<UserStats> findByUserId(UUID userId);

    Page<UserStats> findByCurrentMonthPointsGreaterThanOrderByCurrentMonthPointsDesc(
            int minPoints, Pageable pageable);
}
