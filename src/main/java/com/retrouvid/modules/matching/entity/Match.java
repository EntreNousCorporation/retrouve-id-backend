package com.retrouvid.modules.matching.entity;

import com.retrouvid.modules.admin.entity.RelayPoint;
import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "declaration_perte_id", nullable = false)
    private Declaration declarationPerte;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "declaration_decouverte_id", nullable = false)
    private Declaration declarationDecouverte;

    @Column(nullable = false)
    private double score;

    @Column(name = "verification_score")
    private Double verificationScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MatchStatus status = MatchStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relay_point_id")
    private RelayPoint relayPoint;

    @Column(name = "handover_deadline")
    private Instant handoverDeadline;

    @Column(name = "code_hash", length = 128)
    private String codeHash;

    @Column(name = "code_expires_at")
    private Instant codeExpiresAt;

    @Column(name = "dropped_at")
    private Instant droppedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "failed_pickup_attempts", nullable = false)
    @Builder.Default
    private int failedPickupAttempts = 0;

    @Column(name = "pickup_locked_until")
    private Instant pickupLockedUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_user_id")
    private User agentUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }
}
