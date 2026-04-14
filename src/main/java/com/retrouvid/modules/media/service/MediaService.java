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
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        return upload(ownerId, file, true);
    }

    @Transactional
    public MediaAsset upload(UUID ownerId, MultipartFile file, boolean blur) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Fichier vide");
        if (file.getSize() > MAX_SIZE) throw ApiException.badRequest("Fichier trop volumineux (max 10 Mo)");
        try {
            return store(ownerId, file.getBytes(), file.getContentType(), blur);
        } catch (IOException e) {
            throw ApiException.badRequest("Échec lecture fichier: " + e.getMessage());
        }
    }

    /**
     * Upload via data URI base64 ("data:image/png;base64,...."). Pattern
     * calqué sur pansy-backend : le mobile encode, le serveur décode et
     * applique les mêmes contrôles que l'upload multipart.
     */
    @Transactional
    public MediaAsset uploadBase64(UUID ownerId, String dataUri, boolean blur) {
        if (dataUri == null || dataUri.isBlank()) {
            throw ApiException.badRequest("Contenu vide");
        }
        String type;
        String payload;
        int comma = dataUri.indexOf(',');
        if (dataUri.startsWith("data:") && comma > 0) {
            String header = dataUri.substring(5, comma); // ex: image/png;base64
            int semi = header.indexOf(';');
            type = semi > 0 ? header.substring(0, semi) : header;
            payload = dataUri.substring(comma + 1);
        } else {
            // Pas de préfixe data URI : on suppose du JPEG.
            type = "image/jpeg";
            payload = dataUri;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Base64 invalide");
        }
        if (bytes.length == 0) throw ApiException.badRequest("Fichier vide");
        if (bytes.length > MAX_SIZE) throw ApiException.badRequest("Fichier trop volumineux (max 10 Mo)");
        return store(ownerId, bytes, type, blur);
    }

    private MediaAsset store(UUID ownerId, byte[] original, String type, boolean blur) {
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw ApiException.badRequest("Type non supporté (jpeg, png, webp)");
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.unauthorized("Utilisateur introuvable"));
        try {
            byte[] preview = blur ? blurService.buildPreview(original, type) : original;

            UUID id = UUID.randomUUID();
            Path base = Paths.get(storagePath);
            Files.createDirectories(base);
            String ext = type.contains("png") ? ".png" : type.contains("webp") ? ".webp" : ".jpg";
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

    /**
     * Suppression RGPD : efface les fichiers disque de tous les médias
     * appartenant à un user. La ligne MediaAsset elle-même est supprimée par
     * la cascade SQL déclenchée par la suppression du user parent.
     */
    @Transactional(readOnly = true)
    public void deleteFilesForOwner(UUID ownerId) {
        List<MediaAsset> assets = repository.findByOwnerId(ownerId);
        for (MediaAsset a : assets) {
            deleteFileQuietly(a.getOriginalPath());
            deleteFileQuietly(a.getPreviewPath());
        }
    }

    private void deleteFileQuietly(String path) {
        if (path == null || path.isBlank()) return;
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            log.warn("Impossible de supprimer {} : {}", path, e.getMessage());
        }
    }
}
