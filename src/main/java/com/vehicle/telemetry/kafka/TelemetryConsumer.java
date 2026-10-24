package com.vehicle.telemetry.kafka;

import com.vehicle.telemetry.config.KafkaConfig;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.service.TelemetryProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);

    private final TelemetryProcessingService processingService;

    public TelemetryConsumer(TelemetryProcessingService processingService) {
        this.processingService = processingService;
    }

    @KafkaListener(
            id = KafkaConfig.LISTENER_ID,
            topics = "${vehicle.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(TelemetryEvent event, Acknowledgment acknowledgment) {
        log.info("Kafka consumption eventId={} vehicleId={}", event.eventId(), event.vehicleId());
        try {
            processingService.process(event);
            acknowledgment.acknowledge();
        } catch (RuntimeException exception) {
            log.error("processing failure eventId={}", event.eventId(), exception);
            throw exception;
        }
    }
}
