package com.vehicle.telemetry.kafka;

import com.vehicle.telemetry.AbstractIntegrationTest;
import com.vehicle.telemetry.KafkaGroupSupport;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.repository.AlertRepository;
import com.vehicle.telemetry.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ConsumerRestartIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TelemetryProducer producer;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TelemetryRepository telemetryRepository;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${vehicle.kafka.topic}")
    private String topic;

    @Test
    void reprocessingAfterConsumerRestartDoesNotDuplicateAlerts() throws Exception {
        String eventId = "evt-restart-" + UUID.randomUUID();
        producer.publish(new TelemetryEvent(
                eventId,
                "VH-RESTART",
                Instant.parse("2026-10-24T12:30:00Z"),
                160,
                70
        ));

        await().atMost(Duration.ofSeconds(30)).until(() -> alertRepository.countByEventId(eventId) == 1);

        KafkaGroupSupport kafka = kafka();
        long end = kafka.endOffset();
        await().atMost(Duration.ofSeconds(30)).until(() -> kafka.committedOffset() >= end);
        long totalAlerts = alertRepository.count();

        MessageListenerContainer container = listenerContainer();
        container.stop();
        try {
            await().atMost(Duration.ofSeconds(60)).until(() -> !container.isRunning() && !kafka.hasMembers());
            kafka.resetOffsetsToZero();
            assertThat(kafka.committedOffset()).isZero();
        } finally {
            if (!container.isRunning()) {
                container.start();
            }
        }

        await().atMost(Duration.ofSeconds(60)).until(() -> kafka.committedOffset() >= end);

        assertThat(alertRepository.countByEventId(eventId)).isEqualTo(1);
        assertThat(telemetryRepository.countByEventId(eventId)).isEqualTo(1);
        assertThat(alertRepository.count()).isEqualTo(totalAlerts);
    }

    private KafkaGroupSupport kafka() {
        return new KafkaGroupSupport(bootstrapServers, groupId, topic);
    }
}
