package com.vehicle.telemetry.rules;

import com.vehicle.telemetry.config.RuleProperties;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.entity.AlertType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OverheatingRule implements TelemetryRule {

    private final RuleProperties ruleProperties;

    public OverheatingRule(RuleProperties ruleProperties) {
        this.ruleProperties = ruleProperties;
    }

    @Override
    public Optional<RuleViolation> evaluate(TelemetryEvent event) {
        double threshold = ruleProperties.getTemperatureThreshold();
        if (event.engineTemperature() > threshold) {
            String message = "Vehicle %s engine temperature %.1f C exceeds threshold %.1f C"
                    .formatted(event.vehicleId(), event.engineTemperature(), threshold);
            return Optional.of(new RuleViolation(AlertType.ENGINE_OVERHEATING, message));
        }
        return Optional.empty();
    }
}
