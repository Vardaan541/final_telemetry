package com.vehicle.telemetry.dto;

public record TelemetryAcceptedResponse(
        String eventId,
        String status
) {
}
