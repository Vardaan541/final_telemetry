package com.vehicle.telemetry.kafka;

import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.exception.KafkaPublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class TelemetryProducer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProducer.class);

    private final KafkaTemplate<String, TelemetryEvent> kafkaTemplate;

    private final String topic;

    public TelemetryProducer(
            KafkaTemplate<String, TelemetryEvent> kafkaTemplate,
            @Value("${vehicle.kafka.topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(TelemetryEvent event) {
        try {
            kafkaTemplate.send(topic, event.eventId(), event).get(10, TimeUnit.SECONDS);
            log.info("Kafka publish eventId={} topic={}", event.eventId(), topic);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException("Failed to publish telemetry event", exception);
        } catch (Exception exception) {
            throw new KafkaPublishException("Failed to publish telemetry event", exception);
        }
    }
}
