package com.vehicle.telemetry.dto;

import java.time.Instant;

public record TelemetryEvent(
        String eventId,
        String vehicleId,
        Instant timestamp,
        double speed,
        double engineTemperature
) {
    public static TelemetryEvent from(TelemetryRequest request) {
        return new TelemetryEvent(
                request.eventId(),
                request.vehicleId(),
                request.timestamp(),
                request.speed(),
                request.engineTemperature()
        );
    }
}
