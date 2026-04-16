package com.retrouvid.modules.gamification.entity;

import com.retrouvid.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_badges")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserBadge {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "badge_id", nullable = false)
    private Badge badge;

    /** Pour badges "collection" (ex: ECLAIREUR par ville), discrimine les
     *  instances. Null pour badges uniques. */
    @Column(name = "metadata_key", length = 120)
    private String metadataKey;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    @PrePersist
    void onCreate() { this.earnedAt = Instant.now(); }
}
