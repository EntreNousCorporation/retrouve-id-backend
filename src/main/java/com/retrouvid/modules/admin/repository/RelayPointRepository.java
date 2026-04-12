package com.retrouvid.modules.admin.repository;

import com.retrouvid.modules.admin.entity.RelayPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RelayPointRepository extends JpaRepository<RelayPoint, UUID> {
}
