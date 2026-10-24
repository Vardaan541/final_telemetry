package com.vehicle.telemetry.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record TelemetryRequest(
        @NotBlank(message = "eventId is required")
        String eventId,

        @NotBlank(message = "vehicleId is required")
        String vehicleId,

        @NotNull(message = "timestamp is required")
        Instant timestamp,

        @NotNull(message = "speed is required")
        @DecimalMin(value = "0.0", message = "speed cannot be negative")
        Double speed,

        @NotNull(message = "engineTemperature is required")
        Double engineTemperature
) {
}
