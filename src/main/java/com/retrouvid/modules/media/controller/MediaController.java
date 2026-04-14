package com.retrouvid.modules.media.controller;

import com.retrouvid.modules.media.entity.MediaAsset;
import com.retrouvid.modules.media.service.MediaService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    public record UploadResponse(UUID id, String previewUrl, String originalUrl, String contentType, long size) {}

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "blur", defaultValue = "true") boolean blur) {
        return ApiResponse.ok(toResponse(mediaService.upload(CurrentUser.id(), file, blur)));
    }

    public record Base64UploadRequest(@NotBlank String base64, Boolean blur) {}

    /**
     * Upload via base64 data URI (ex: "data:image/png;base64,...."), aligné
     * sur le pattern pansy-backend. Plus robuste côté mobile que le multipart.
     */
    @PostMapping(value = "/upload-base64", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<UploadResponse> uploadBase64(@Valid @RequestBody Base64UploadRequest req) {
        boolean blur = req.blur() == null || req.blur();
        return ApiResponse.ok(toResponse(mediaService.uploadBase64(CurrentUser.id(), req.base64(), blur)));
    }

    private UploadResponse toResponse(MediaAsset asset) {
        return new UploadResponse(
                asset.getId(),
                "/api/v1/media/" + asset.getId() + "/preview",
                "/api/v1/media/" + asset.getId() + "/original",
                asset.getContentType(),
                asset.getSizeBytes());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable UUID id) throws IOException {
        MediaAsset asset = mediaService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .body(mediaService.readPreview(asset));
    }

    @GetMapping("/{id}/original")
    public ResponseEntity<byte[]> original(@PathVariable UUID id) throws IOException {
        MediaAsset asset = mediaService.get(id);
        byte[] data = mediaService.readOriginal(asset, CurrentUser.id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .body(data);
    }
}
