package com.vehicle.telemetry.dto;

import java.util.List;

public record AlertPageResponse(
        List<AlertResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
