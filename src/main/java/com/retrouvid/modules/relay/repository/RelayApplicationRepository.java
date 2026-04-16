package com.retrouvid.modules.relay.repository;

import com.retrouvid.modules.relay.entity.RelayApplication;
import com.retrouvid.modules.relay.entity.RelayApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelayApplicationRepository extends JpaRepository<RelayApplication, UUID> {

    Optional<RelayApplication> findByUserIdAndStatus(UUID userId, RelayApplicationStatus status);

    List<RelayApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<RelayApplication> findByStatus(RelayApplicationStatus status, Pageable pageable);

    Page<RelayApplication> findAll(Pageable pageable);
}
