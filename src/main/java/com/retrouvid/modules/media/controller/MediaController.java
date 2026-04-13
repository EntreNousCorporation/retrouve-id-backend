package com.retrouvid.modules.media.controller;

import com.retrouvid.modules.media.entity.MediaAsset;
import com.retrouvid.modules.media.service.MediaService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.exception.ApiException;
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
        MediaAsset asset = mediaService.upload(CurrentUser.id(), file, blur);
        return ApiResponse.ok(new UploadResponse(
                asset.getId(),
                "/api/v1/media/" + asset.getId() + "/preview",
                "/api/v1/media/" + asset.getId() + "/original",
                asset.getContentType(),
                asset.getSizeBytes()));
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
