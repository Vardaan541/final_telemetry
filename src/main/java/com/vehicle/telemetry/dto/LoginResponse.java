package com.vehicle.telemetry.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn
) {
}
