package com.retrouvid.modules.matching.repository;

import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.entity.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    Optional<Match> findByDeclarationPerteIdAndDeclarationDecouverteId(UUID perteId, UUID decouverteId);

    @Query("""
        SELECT m FROM Match m
        WHERE m.declarationPerte.user.id = :userId OR m.declarationDecouverte.user.id = :userId
        ORDER BY m.createdAt DESC
    """)
    Page<Match> findForUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
        SELECT m FROM Match m
        WHERE (m.declarationPerte.id = :declId OR m.declarationDecouverte.id = :declId)
    """)
    List<Match> findByDeclaration(@Param("declId") UUID declId);

    List<Match> findByStatus(MatchStatus status);
}
