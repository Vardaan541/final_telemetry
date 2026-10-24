package com.vehicle.telemetry.rules;

import com.vehicle.telemetry.entity.AlertType;

public record RuleViolation(
        AlertType alertType,
        String message
) {
}
