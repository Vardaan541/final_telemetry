package com.vehicle.telemetry.rules;

import com.vehicle.telemetry.config.RuleProperties;
import com.vehicle.telemetry.dto.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<TelemetryRule> rules;

    public RuleEngine(List<TelemetryRule> rules, RuleProperties ruleProperties) {
        this.rules = rules;
        log.info(
                "Rule thresholds configured speed={} km/h temperature={} C",
                ruleProperties.getSpeedThreshold(),
                ruleProperties.getTemperatureThreshold()
        );
    }

    public List<RuleViolation> evaluate(TelemetryEvent event) {
        List<RuleViolation> violations = new ArrayList<>();
        for (TelemetryRule rule : rules) {
            rule.evaluate(event).ifPresent(violation -> {
                log.info(
                        "rule violation eventId={} vehicleId={} alertType={}",
                        event.eventId(),
                        event.vehicleId(),
                        violation.alertType()
                );
                violations.add(violation);
            });
        }
        return violations;
    }
}
