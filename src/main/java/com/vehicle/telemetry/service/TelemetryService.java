package com.vehicle.telemetry.service;

import com.vehicle.telemetry.dto.TelemetryAcceptedResponse;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.dto.TelemetryRequest;
import com.vehicle.telemetry.kafka.TelemetryProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryProducer telemetryProducer;

    public TelemetryService(TelemetryProducer telemetryProducer) {
        this.telemetryProducer = telemetryProducer;
    }

    public TelemetryAcceptedResponse ingest(TelemetryRequest request) {
        log.info("telemetry received eventId={} vehicleId={}", request.eventId(), request.vehicleId());
        TelemetryEvent event = TelemetryEvent.from(request);
        telemetryProducer.publish(event);
        return new TelemetryAcceptedResponse(event.eventId(), "PUBLISHED");
    }
}
