package com.vehicle.telemetry.controller;

import com.vehicle.telemetry.dto.TelemetryAcceptedResponse;
import com.vehicle.telemetry.dto.TelemetryRequest;
import com.vehicle.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<TelemetryAcceptedResponse> ingest(@Valid @RequestBody TelemetryRequest request) {
        return ResponseEntity.accepted().body(telemetryService.ingest(request));
    }
}
