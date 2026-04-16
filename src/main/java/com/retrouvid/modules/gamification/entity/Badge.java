package com.retrouvid.modules.gamification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "badges")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Badge {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 16)
    private String icon;

    /** Seuil (restitutions ou sous-compte) ; null si non pertinent. */
    private Integer threshold;
}
