package com.vehicle.telemetry.service;

import com.vehicle.telemetry.dto.AlertPageResponse;
import com.vehicle.telemetry.dto.AlertResponse;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.entity.Alert;
import com.vehicle.telemetry.entity.AlertType;
import com.vehicle.telemetry.repository.AlertRepository;
import com.vehicle.telemetry.repository.AlertSpecifications;
import com.vehicle.telemetry.rules.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;

    private final IdempotentExecutor idempotentExecutor;

    private final AlertStream alertStream;

    public AlertService(
            AlertRepository alertRepository,
            IdempotentExecutor idempotentExecutor,
            AlertStream alertStream
    ) {
        this.alertRepository = alertRepository;
        this.idempotentExecutor = idempotentExecutor;
        this.alertStream = alertStream;
    }

    public void createAlert(TelemetryEvent event, RuleViolation violation) {
        Alert alert = new Alert();
        alert.setEventId(event.eventId());
        alert.setVehicleId(event.vehicleId());
        alert.setAlertType(violation.alertType());
        alert.setMessage(violation.message());
        alert.setTimestamp(event.timestamp());
        boolean created = idempotentExecutor.run(() -> {
            alertRepository.saveAndFlush(alert);
            log.info(
                    "alert creation eventId={} vehicleId={} alertType={}",
                    event.eventId(),
                    event.vehicleId(),
                    violation.alertType()
            );
        }, "duplicate event detection eventId=" + event.eventId() + " alertType=" + violation.alertType());
        if (created) {
            alertStream.publish(AlertResponse.from(alert));
        }
    }

    public AlertPageResponse findAlerts(
            String vehicleId,
            AlertType alertType,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        String normalizedVehicleId = vehicleId == null || vehicleId.isBlank() ? null : vehicleId;
        Page<Alert> page = alertRepository.findAll(
                AlertSpecifications.filter(normalizedVehicleId, alertType, from, to),
                pageable
        );
        List<AlertResponse> content = page.getContent().stream().map(AlertResponse::from).toList();
        return new AlertPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
