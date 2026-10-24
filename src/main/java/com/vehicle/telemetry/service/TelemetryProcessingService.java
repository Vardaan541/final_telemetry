package com.vehicle.telemetry.service;

import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.entity.Telemetry;
import com.vehicle.telemetry.repository.TelemetryRepository;
import com.vehicle.telemetry.rules.RuleEngine;
import com.vehicle.telemetry.rules.RuleViolation;
import org.springframework.stereotype.Service;

@Service
public class TelemetryProcessingService {

    private final TelemetryRepository telemetryRepository;

    private final RuleEngine ruleEngine;

    private final AlertService alertService;

    private final IdempotentExecutor idempotentExecutor;

    public TelemetryProcessingService(
            TelemetryRepository telemetryRepository,
            RuleEngine ruleEngine,
            AlertService alertService,
            IdempotentExecutor idempotentExecutor
    ) {
        this.telemetryRepository = telemetryRepository;
        this.ruleEngine = ruleEngine;
        this.alertService = alertService;
        this.idempotentExecutor = idempotentExecutor;
    }

    public void process(TelemetryEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("Telemetry eventId is required");
        }
        persistTelemetry(event);
        for (RuleViolation violation : ruleEngine.evaluate(event)) {
            alertService.createAlert(event, violation);
        }
    }

    private void persistTelemetry(TelemetryEvent event) {
        idempotentExecutor.run(() -> {
            Telemetry telemetry = new Telemetry();
            telemetry.setEventId(event.eventId());
            telemetry.setVehicleId(event.vehicleId());
            telemetry.setTimestamp(event.timestamp());
            telemetry.setSpeed(event.speed());
            telemetry.setEngineTemperature(event.engineTemperature());
            telemetryRepository.saveAndFlush(telemetry);
        }, "duplicate event detection eventId=" + event.eventId());
    }
}
