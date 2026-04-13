package com.retrouvid.modules.media.repository;

import com.retrouvid.modules.media.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    List<MediaAsset> findByOwnerId(UUID ownerId);
}
