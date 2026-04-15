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

    long countByStatus(MatchStatus status);

    /// Score du meilleur match pour une déclaration (côté perte ou
    /// découverte). Renvoie une liste de [declarationId, maxScore] pour
    /// éviter le N+1 quand on hydrate une page de déclarations.
    @Query("""
        SELECT d.id, MAX(m.score) FROM Match m
        JOIN Declaration d ON d.id = m.declarationPerte.id OR d.id = m.declarationDecouverte.id
        WHERE d.id IN :declarationIds
        GROUP BY d.id
    """)
    List<Object[]> bestScoresFor(@Param("declarationIds") List<UUID> declarationIds);
}
