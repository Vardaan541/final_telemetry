package com.vehicle.telemetry.rules;

import com.vehicle.telemetry.dto.TelemetryEvent;

import java.util.Optional;

public interface TelemetryRule {

    Optional<RuleViolation> evaluate(TelemetryEvent event);
}
