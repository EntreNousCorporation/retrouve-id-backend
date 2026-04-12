package com.retrouvid.modules.declaration.service;

import com.retrouvid.modules.declaration.dto.DeclarationRequest;
import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.entity.DocumentType;
import com.retrouvid.modules.declaration.repository.DeclarationRepository;
import com.retrouvid.modules.matching.service.MatchingService;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.shared.exception.ApiException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeclarationService {

    private final DeclarationRepository declarationRepository;
    private final UserRepository userRepository;
    private final MatchingService matchingService;

    @Transactional
    public Declaration create(UUID userId, DeclarationRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Utilisateur introuvable"));
        Declaration d = Declaration.builder()
                .user(user)
                .type(req.type())
                .documentType(req.documentType())
                .documentNumberPartial(req.documentNumberPartial())
                .ownerName(req.ownerName())
                .description(req.description())
                .photoUrl(req.photoUrl())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .locationDescription(req.locationDescription())
                .city(req.city())
                .dateEvent(req.dateEvent())
                .status(DeclarationStatus.ACTIVE)
                .build();
        Declaration saved = declarationRepository.save(d);
        matchingService.computeForDeclaration(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Declaration get(UUID id) {
        return declarationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Déclaration introuvable"));
    }

    @Transactional
    public Declaration update(UUID id, UUID userId, DeclarationRequest req) {
        Declaration d = get(id);
        if (!d.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Vous ne pouvez modifier que vos déclarations");
        }
        d.setType(req.type());
        d.setDocumentType(req.documentType());
        d.setDocumentNumberPartial(req.documentNumberPartial());
        d.setOwnerName(req.ownerName());
        d.setDescription(req.description());
        d.setPhotoUrl(req.photoUrl());
        d.setLatitude(req.latitude());
        d.setLongitude(req.longitude());
        d.setLocationDescription(req.locationDescription());
        d.setCity(req.city());
        d.setDateEvent(req.dateEvent());
        return d;
    }

    @Transactional
    public Declaration changeStatus(UUID id, UUID userId, DeclarationStatus status) {
        Declaration d = get(id);
        if (!d.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Action non autorisée");
        }
        d.setStatus(status);
        return d;
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Declaration d = get(id);
        if (!d.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Action non autorisée");
        }
        declarationRepository.delete(d);
    }

    @Transactional(readOnly = true)
    public Page<Declaration> search(DeclarationType type, DocumentType documentType, String city, String ownerName, DeclarationStatus status, Pageable pageable) {
        Specification<Declaration> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (type != null) ps.add(cb.equal(root.get("type"), type));
            if (documentType != null) ps.add(cb.equal(root.get("documentType"), documentType));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (city != null && !city.isBlank()) ps.add(cb.equal(cb.lower(root.get("city")), city.toLowerCase()));
            if (ownerName != null && !ownerName.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("ownerName")), "%" + ownerName.toLowerCase() + "%"));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return declarationRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Declaration> findMine(UUID userId, Pageable pageable) {
        return declarationRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Declaration> findNearby(double lat, double lon, double radiusKm, Pageable pageable) {
        double latDelta = radiusKm / 111.0;
        double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        Specification<Declaration> spec = (root, q, cb) -> cb.and(
                cb.isNotNull(root.get("latitude")),
                cb.isNotNull(root.get("longitude")),
                cb.between(root.get("latitude"), lat - latDelta, lat + latDelta),
                cb.between(root.get("longitude"), lon - lonDelta, lon + lonDelta),
                cb.equal(root.get("status"), DeclarationStatus.ACTIVE)
        );
        return declarationRepository.findAll(spec, pageable);
    }
}
