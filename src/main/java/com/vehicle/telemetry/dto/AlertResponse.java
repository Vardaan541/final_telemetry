package com.vehicle.telemetry.dto;

import com.vehicle.telemetry.entity.Alert;
import com.vehicle.telemetry.entity.AlertType;

import java.time.Instant;

public record AlertResponse(
        Long id,
        String eventId,
        String vehicleId,
        AlertType alertType,
        String message,
        Instant timestamp
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getEventId(),
                alert.getVehicleId(),
                alert.getAlertType(),
                alert.getMessage(),
                alert.getTimestamp()
        );
    }
}
