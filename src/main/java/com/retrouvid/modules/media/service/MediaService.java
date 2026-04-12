package com.retrouvid.modules.media.service;

import com.retrouvid.modules.media.entity.MediaAsset;
import com.retrouvid.modules.media.repository.MediaAssetRepository;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final MediaAssetRepository repository;
    private final UserRepository userRepository;
    private final ImageBlurService blurService;

    @Value("${app.media.storage-path:./storage/media}")
    private String storagePath;

    @Transactional
    public MediaAsset upload(UUID ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Fichier vide");
        if (file.getSize() > MAX_SIZE) throw ApiException.badRequest("Fichier trop volumineux (max 10 Mo)");
        String type = file.getContentType();
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw ApiException.badRequest("Type non supporté (jpeg, png, webp)");
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.unauthorized("Utilisateur introuvable"));

        try {
            byte[] original = file.getBytes();
            byte[] preview = blurService.buildPreview(original, type);

            UUID id = UUID.randomUUID();
            Path base = Paths.get(storagePath);
            Files.createDirectories(base);
            String ext = type.contains("png") ? ".png" : ".jpg";
            Path originalPath = base.resolve(id + "-original" + ext);
            Path previewPath = base.resolve(id + "-preview" + ext);
            Files.write(originalPath, original);
            Files.write(previewPath, preview);

            MediaAsset asset = MediaAsset.builder()
                    .id(id)
                    .owner(owner)
                    .originalPath(originalPath.toString())
                    .previewPath(previewPath.toString())
                    .contentType(type)
                    .sizeBytes(original.length)
                    .build();
            return repository.save(asset);
        } catch (IOException e) {
            throw ApiException.badRequest("Échec traitement image: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public MediaAsset get(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Média introuvable"));
    }

    public byte[] readPreview(MediaAsset asset) throws IOException {
        return Files.readAllBytes(Path.of(asset.getPreviewPath()));
    }

    public byte[] readOriginal(MediaAsset asset, UUID requesterId) throws IOException {
        if (!asset.getOwner().getId().equals(requesterId)) {
            throw ApiException.forbidden("Accès au fichier original réservé au propriétaire");
        }
        return Files.readAllBytes(Path.of(asset.getOriginalPath()));
    }
}
