package com.vehicle.telemetry.controller;

import com.vehicle.telemetry.dto.AlertPageResponse;
import com.vehicle.telemetry.entity.AlertType;
import com.vehicle.telemetry.exception.InvalidRequestException;
import com.vehicle.telemetry.service.AlertService;
import com.vehicle.telemetry.service.AlertStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AlertService alertService;

    private final AlertStream alertStream;

    public AlertController(AlertService alertService, AlertStream alertStream) {
        this.alertService = alertService;
        this.alertStream = alertStream;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlerts() {
        return alertStream.openStream();
    }

    @GetMapping
    public AlertPageResponse getAlerts(
            @RequestParam(required = false) String vehicleId,
            @RequestParam(required = false) AlertType alertType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return alertService.findAlerts(vehicleId, alertType, from, to, pageRequest);
    }
}
