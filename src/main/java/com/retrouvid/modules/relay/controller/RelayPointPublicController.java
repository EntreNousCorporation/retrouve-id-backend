package com.retrouvid.modules.relay.controller;

import com.retrouvid.modules.admin.entity.RelayPoint;
import com.retrouvid.modules.admin.repository.RelayPointRepository;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/relay-points")
@RequiredArgsConstructor
public class RelayPointPublicController {

    private final RelayPointRepository relayPointRepository;

    public record RelayPointDto(UUID id, String name, String type, String address,
                                Double latitude, Double longitude, String city,
                                String phone) {
        static RelayPointDto from(RelayPoint r) {
            return new RelayPointDto(r.getId(), r.getName(), r.getType(), r.getAddress(),
                    r.getLatitude(), r.getLongitude(), r.getCity(), r.getPhone());
        }
    }

    @GetMapping("/nearby")
    public ApiResponse<PagedResponse<RelayPointDto>> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "10") double radiusKm,
            @PageableDefault(size = 20) Pageable pageable) {
        var page = relayPointRepository.findNearbyHaversine(lat, lon, radiusKm, pageable)
                .map(RelayPointDto::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }
}
