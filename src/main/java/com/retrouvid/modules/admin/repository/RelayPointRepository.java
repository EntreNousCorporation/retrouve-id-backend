package com.retrouvid.modules.admin.repository;

import com.retrouvid.modules.admin.entity.RelayPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RelayPointRepository extends JpaRepository<RelayPoint, UUID> {

    Optional<RelayPoint> findByUserId(UUID userId);

    @Query(value = """
            SELECT * FROM relay_points r
            WHERE r.is_active = TRUE
              AND r.latitude IS NOT NULL AND r.longitude IS NOT NULL
              AND (6371 * acos(
                   LEAST(1, GREATEST(-1,
                     cos(radians(:lat)) * cos(radians(r.latitude))
                     * cos(radians(r.longitude) - radians(:lon))
                     + sin(radians(:lat)) * sin(radians(r.latitude))
                   ))
                 )) <= :radiusKm
            ORDER BY (6371 * acos(
                   LEAST(1, GREATEST(-1,
                     cos(radians(:lat)) * cos(radians(r.latitude))
                     * cos(radians(r.longitude) - radians(:lon))
                     + sin(radians(:lat)) * sin(radians(r.latitude))
                   ))
                 )) ASC
            """,
            countQuery = """
            SELECT count(*) FROM relay_points r
            WHERE r.is_active = TRUE
              AND r.latitude IS NOT NULL AND r.longitude IS NOT NULL
              AND (6371 * acos(
                   LEAST(1, GREATEST(-1,
                     cos(radians(:lat)) * cos(radians(r.latitude))
                     * cos(radians(r.longitude) - radians(:lon))
                     + sin(radians(:lat)) * sin(radians(r.latitude))
                   ))
                 )) <= :radiusKm
            """,
            nativeQuery = true)
    Page<RelayPoint> findNearbyHaversine(@Param("lat") double lat,
                                         @Param("lon") double lon,
                                         @Param("radiusKm") double radiusKm,
                                         Pageable pageable);
}
