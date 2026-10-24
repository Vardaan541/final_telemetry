package com.vehicle.telemetry.rules;

import com.vehicle.telemetry.config.RuleProperties;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.entity.AlertType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SpeedingRuleTest {

    private final SpeedingRule rule = new SpeedingRule(properties());

    @Test
    void speedAboveThresholdCreatesSpeedingAlert() {
        TelemetryEvent event = event(120.1, 90);

        assertThat(rule.evaluate(event)).isPresent();
        RuleViolation violation = rule.evaluate(event).orElseThrow();
        assertThat(violation.alertType()).isEqualTo(AlertType.SPEEDING);
        assertThat(violation.message()).contains("VH-001").contains("120.1");
    }

    @Test
    void speedEqualToThresholdDoesNotCreateAlert() {
        assertThat(rule.evaluate(event(120.0, 90))).isEmpty();
    }

    @Test
    void speedBelowThresholdDoesNotCreateAlert() {
        assertThat(rule.evaluate(event(80.0, 90))).isEmpty();
    }

    private static RuleProperties properties() {
        RuleProperties properties = new RuleProperties();
        properties.setSpeedThreshold(120);
        properties.setTemperatureThreshold(100);
        return properties;
    }

    private static TelemetryEvent event(double speed, double temperature) {
        return new TelemetryEvent("evt-speed", "VH-001", Instant.parse("2026-10-24T12:30:00Z"), speed, temperature);
    }
}
