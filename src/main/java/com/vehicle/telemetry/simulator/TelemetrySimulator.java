package com.vehicle.telemetry.simulator;

import com.vehicle.telemetry.config.SimulatorProperties;
import com.vehicle.telemetry.dto.TelemetryRequest;
import com.vehicle.telemetry.service.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "vehicle.simulator", name = "enabled", havingValue = "true")
public class TelemetrySimulator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySimulator.class);

    private static final double[] SPEEDS = {80.0, 140.0, 60.0, 150.0};

    private static final double[] TEMPERATURES = {90.0, 80.0, 115.0, 120.0};

    private final TelemetryService telemetryService;

    private final SimulatorProperties simulatorProperties;

    public TelemetrySimulator(TelemetryService telemetryService, SimulatorProperties simulatorProperties) {
        this.telemetryService = telemetryService;
        this.simulatorProperties = simulatorProperties;
    }

    @Override
    public void run(String... args) {
        int count = simulatorProperties.getCount();
        log.info("Generating {} simulated telemetry events", count);
        long batch = Instant.now().toEpochMilli();
        for (int index = 0; index < count; index++) {
            TelemetryRequest request = sample(batch, index);
            log.info(
                    "simulated telemetry eventId={} vehicleId={} speed={} engineTemperature={}",
                    request.eventId(),
                    request.vehicleId(),
                    request.speed(),
                    request.engineTemperature()
            );
            telemetryService.ingest(request);
        }
    }

    private TelemetryRequest sample(long batch, int index) {
        int pattern = index % SPEEDS.length;
        return new TelemetryRequest(
                "sim-" + batch + "-" + index,
                "VH-" + String.format("%03d", (index % 3) + 1),
                Instant.now(),
                SPEEDS[pattern],
                TEMPERATURES[pattern]
        );
    }
}
