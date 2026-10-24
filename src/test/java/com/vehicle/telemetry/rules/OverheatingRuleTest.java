package com.vehicle.telemetry.rules;

import com.vehicle.telemetry.config.RuleProperties;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.entity.AlertType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OverheatingRuleTest {

    private final OverheatingRule rule = new OverheatingRule(properties());

    @Test
    void temperatureAboveThresholdCreatesOverheatingAlert() {
        RuleViolation violation = rule.evaluate(event(80, 100.1)).orElseThrow();

        assertThat(violation.alertType()).isEqualTo(AlertType.ENGINE_OVERHEATING);
        assertThat(violation.message()).contains("VH-001").contains("100.1");
    }

    @Test
    void temperatureEqualToThresholdDoesNotCreateAlert() {
        assertThat(rule.evaluate(event(80, 100.0))).isEmpty();
    }

    @Test
    void temperatureBelowThresholdDoesNotCreateAlert() {
        assertThat(rule.evaluate(event(80, 90.0))).isEmpty();
    }

    @Test
    void oneEventCanViolateBothRules() {
        RuleProperties properties = properties();
        RuleEngine engine = new RuleEngine(
                List.of(new SpeedingRule(properties), new OverheatingRule(properties)),
                properties
        );

        List<RuleViolation> violations = engine.evaluate(event(130, 110));

        assertThat(violations)
                .extracting(RuleViolation::alertType)
                .containsExactlyInAnyOrder(AlertType.SPEEDING, AlertType.ENGINE_OVERHEATING);
    }

    private static RuleProperties properties() {
        RuleProperties properties = new RuleProperties();
        properties.setSpeedThreshold(120);
        properties.setTemperatureThreshold(100);
        return properties;
    }

    private static TelemetryEvent event(double speed, double temperature) {
        return new TelemetryEvent(
                "evt-temp",
                "VH-001",
                Instant.parse("2026-10-24T12:30:00Z"),
                speed,
                temperature
        );
    }
}
