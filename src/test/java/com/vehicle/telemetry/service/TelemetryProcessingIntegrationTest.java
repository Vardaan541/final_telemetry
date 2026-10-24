package com.vehicle.telemetry.service;

import com.vehicle.telemetry.AbstractIntegrationTest;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.entity.Alert;
import com.vehicle.telemetry.entity.AlertType;
import com.vehicle.telemetry.repository.AlertRepository;
import com.vehicle.telemetry.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryProcessingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TelemetryProcessingService processingService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TelemetryRepository telemetryRepository;

    @Test
    void speedingRulePersistsOneSpeedingAlert() {
        String eventId = id("speed");
        processingService.process(event(eventId, "VH-SPEED", 125.5, 80));

        List<Alert> alerts = alertRepository.findByEventId(eventId);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertType.SPEEDING);
        assertThat(alerts.get(0).getVehicleId()).isEqualTo("VH-SPEED");
        assertThat(telemetryRepository.countByEventId(eventId)).isEqualTo(1);
    }

    @Test
    void overheatingRulePersistsOneOverheatingAlert() {
        String eventId = id("heat");
        processingService.process(event(eventId, "VH-HEAT", 80, 105));

        List<Alert> alerts = alertRepository.findByEventId(eventId);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertType.ENGINE_OVERHEATING);
    }

    @Test
    void oneEventThatBreaksBothRulesPersistsTwoAlerts() {
        String eventId = id("both");
        processingService.process(event(eventId, "VH-BOTH", 150, 110));

        assertThat(alertRepository.findByEventId(eventId))
                .extracting(Alert::getAlertType)
                .containsExactlyInAnyOrder(AlertType.SPEEDING, AlertType.ENGINE_OVERHEATING);
    }

    @Test
    void processingTheSameEventTwiceDoesNotDuplicateAlerts() {
        String eventId = id("dup");
        TelemetryEvent event = event(eventId, "VH-DUP", 150, 110);

        processingService.process(event);
        processingService.process(event);

        assertThat(telemetryRepository.countByEventId(eventId)).isEqualTo(1);
        assertThat(alertRepository.countByEventId(eventId)).isEqualTo(2);
        assertThat(alertRepository.findByEventId(eventId))
                .extracting(Alert::getAlertType)
                .containsExactlyInAnyOrder(AlertType.SPEEDING, AlertType.ENGINE_OVERHEATING);
    }

    private static TelemetryEvent event(String eventId, String vehicleId, double speed, double temperature) {
        return new TelemetryEvent(eventId, vehicleId, Instant.parse("2026-10-24T12:30:00Z"), speed, temperature);
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
