package com.vehicle.telemetry.rules;

import com.vehicle.telemetry.config.RuleProperties;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.entity.AlertType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpeedingRule implements TelemetryRule {

    private final RuleProperties ruleProperties;

    public SpeedingRule(RuleProperties ruleProperties) {
        this.ruleProperties = ruleProperties;
    }

    @Override
    public Optional<RuleViolation> evaluate(TelemetryEvent event) {
        double threshold = ruleProperties.getSpeedThreshold();
        if (event.speed() > threshold) {
            String message = "Vehicle %s speed %.1f km/h exceeds threshold %.1f km/h"
                    .formatted(event.vehicleId(), event.speed(), threshold);
            return Optional.of(new RuleViolation(AlertType.SPEEDING, message));
        }
        return Optional.empty();
    }
}
