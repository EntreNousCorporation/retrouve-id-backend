package com.retrouvid.modules.gamification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_stats")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserStats {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private int totalPoints = 0;

    @Column(name = "current_month_points", nullable = false)
    @Builder.Default
    private int currentMonthPoints = 0;

    @Column(name = "month_reset_at")
    private Instant monthResetAt;

    @Column(name = "restitutions_completed", nullable = false)
    @Builder.Default
    private int restitutionsCompleted = 0;

    @Column(name = "public_profile", nullable = false)
    @Builder.Default
    private boolean publicProfile = false;

    @Column(name = "public_alias", length = 80)
    private String publicAlias;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
