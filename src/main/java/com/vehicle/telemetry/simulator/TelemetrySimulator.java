package com.vehicle.telemetry.simulator;

import com.vehicle.telemetry.config.SimulatorProperties;
import com.vehicle.telemetry.dto.TelemetryRequest;
import com.vehicle.telemetry.service.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "vehicle.simulator", name = "enabled", havingValue = "true")
public class TelemetrySimulator {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySimulator.class);

    private static final double[] SPEEDS = {80.0, 140.0, 60.0, 150.0};

    private static final double[] TEMPERATURES = {90.0, 80.0, 115.0, 120.0};

    private final TelemetryService telemetryService;

    private final SimulatorProperties simulatorProperties;

    private final AtomicInteger published = new AtomicInteger();

    public TelemetrySimulator(TelemetryService telemetryService, SimulatorProperties simulatorProperties) {
        this.telemetryService = telemetryService;
        this.simulatorProperties = simulatorProperties;
    }

    @Scheduled(
            fixedDelayString = "${vehicle.simulator.interval-ms:2000}",
            initialDelayString = "${vehicle.simulator.initial-delay-ms:1500}"
    )
    public void publishNext() {
        int limit = simulatorProperties.getCount();
        int index = published.get();
        if (limit > 0 && index >= limit) {
            return;
        }
        TelemetryRequest request = sample(index);
        try {
            telemetryService.ingest(request);
        } catch (RuntimeException exception) {
            log.error("simulated telemetry publish failed eventId={}", request.eventId(), exception);
            return;
        }
        published.incrementAndGet();
        log.info(
                "simulated telemetry eventId={} vehicleId={} speed={} engineTemperature={}",
                request.eventId(),
                request.vehicleId(),
                request.speed(),
                request.engineTemperature()
        );
    }

    private TelemetryRequest sample(int index) {
        int pattern = Math.floorMod(index, SPEEDS.length);
        return new TelemetryRequest(
                "sim-" + System.currentTimeMillis() + "-" + index,
                "VH-" + String.format("%03d", (index % 3) + 1),
                Instant.now(),
                SPEEDS[pattern],
                TEMPERATURES[pattern]
        );
    }
}
