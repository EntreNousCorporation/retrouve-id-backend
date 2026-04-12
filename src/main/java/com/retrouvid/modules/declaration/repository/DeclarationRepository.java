package com.retrouvid.modules.declaration.repository;

import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.entity.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DeclarationRepository extends JpaRepository<Declaration, UUID>, JpaSpecificationExecutor<Declaration> {
    Page<Declaration> findByUserId(UUID userId, Pageable pageable);
    Page<Declaration> findByTypeAndStatus(DeclarationType type, DeclarationStatus status, Pageable pageable);
    Page<Declaration> findByDocumentTypeAndStatus(DocumentType documentType, DeclarationStatus status, Pageable pageable);
}
