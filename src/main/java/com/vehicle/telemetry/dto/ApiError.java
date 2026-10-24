package com.vehicle.telemetry.dto;

public record ApiError(
        int status,
        String error,
        String message
) {
}
