package com.retrouvid.modules.declaration.repository;

import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.entity.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DeclarationRepository extends JpaRepository<Declaration, UUID>, JpaSpecificationExecutor<Declaration> {
    Page<Declaration> findByUserId(UUID userId, Pageable pageable);
    Page<Declaration> findByTypeAndStatus(DeclarationType type, DeclarationStatus status, Pageable pageable);
    Page<Declaration> findByDocumentTypeAndStatus(DocumentType documentType, DeclarationStatus status, Pageable pageable);

    long countByStatus(DeclarationStatus status);
    long countByType(DeclarationType type);

    @Query("select d.documentType, count(d) from Declaration d group by d.documentType")
    List<Object[]> countGroupByDocumentType();

    @Query(value = """
        SELECT *
        FROM declarations d
        WHERE d.status = 'ACTIVE'
          AND d.latitude IS NOT NULL
          AND d.longitude IS NOT NULL
          AND (6371 * acos(
                least(1.0, greatest(-1.0,
                    cos(radians(:lat)) * cos(radians(d.latitude))
                        * cos(radians(d.longitude) - radians(:lon))
                    + sin(radians(:lat)) * sin(radians(d.latitude))
                ))
              )) <= :radiusKm
        ORDER BY (6371 * acos(
                least(1.0, greatest(-1.0,
                    cos(radians(:lat)) * cos(radians(d.latitude))
                        * cos(radians(d.longitude) - radians(:lon))
                    + sin(radians(:lat)) * sin(radians(d.latitude))
                ))
              )) ASC
        """,
            countQuery = """
        SELECT count(*)
        FROM declarations d
        WHERE d.status = 'ACTIVE'
          AND d.latitude IS NOT NULL
          AND d.longitude IS NOT NULL
          AND (6371 * acos(
                least(1.0, greatest(-1.0,
                    cos(radians(:lat)) * cos(radians(d.latitude))
                        * cos(radians(d.longitude) - radians(:lon))
                    + sin(radians(:lat)) * sin(radians(d.latitude))
                ))
              )) <= :radiusKm
        """,
            nativeQuery = true)
    Page<Declaration> findNearbyHaversine(
            @org.springframework.data.repository.query.Param("lat") double lat,
            @org.springframework.data.repository.query.Param("lon") double lon,
            @org.springframework.data.repository.query.Param("radiusKm") double radiusKm,
            Pageable pageable);
}
