package com.vehicle.telemetry.kafka;

import com.vehicle.telemetry.AbstractIntegrationTest;
import com.vehicle.telemetry.KafkaGroupSupport;
import com.vehicle.telemetry.dto.TelemetryEvent;
import com.vehicle.telemetry.entity.AlertType;
import com.vehicle.telemetry.repository.AlertRepository;
import com.vehicle.telemetry.repository.TelemetryRepository;
import com.vehicle.telemetry.service.TelemetryProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DatabaseFailureRecoveryTest extends AbstractIntegrationTest {

    @Autowired
    private TelemetryConsumer consumer;

    @Autowired
    private TelemetryProducer producer;

    @Autowired
    private TelemetryProcessingService processingService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TelemetryRepository telemetryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${vehicle.kafka.topic}")
    private String topic;

    @Test
    void persistenceFailureIsNotAcknowledgedAndRecoveryDoesNotDuplicateAlerts() throws Exception {
        MessageListenerContainer container = listenerContainer();
        container.stop();
        await().atMost(Duration.ofSeconds(60)).until(() -> !container.isRunning());

        String directEventId = "evt-db-direct-" + UUID.randomUUID();
        String kafkaEventId = "evt-db-kafka-" + UUID.randomUUID();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        boolean renamed = false;

        try {
            jdbcTemplate.execute("ALTER TABLE alert RENAME TO alert_unavailable");
            renamed = true;

            assertThatThrownBy(() -> consumer.consume(event(directEventId, 150, 80), acknowledgment))
                    .isInstanceOf(RuntimeException.class);
            verify(acknowledgment, never()).acknowledge();

            producer.publish(event(kafkaEventId, 155, 70));
            KafkaGroupSupport kafka = kafka();
            long end = kafka.endOffset();
            container.start();
            await().atMost(Duration.ofSeconds(20)).until(kafka::hasMembers);

            await().during(Duration.ofSeconds(3))
                    .atMost(Duration.ofSeconds(8))
                    .until(() -> alertCount(kafkaEventId) == 0 && kafka.committedOffset() < end);

            jdbcTemplate.execute("ALTER TABLE alert_unavailable RENAME TO alert");
            renamed = false;

            await().atMost(Duration.ofSeconds(30)).until(() ->
                    alertCount(kafkaEventId) == 1 && kafka.committedOffset() >= end);

            processingService.process(event(kafkaEventId, 155, 70));
            consumer.consume(event(directEventId, 150, 80), acknowledgment);
            consumer.consume(event(directEventId, 150, 80), acknowledgment);

            verify(acknowledgment, times(2)).acknowledge();
            assertThat(alertRepository.findByEventId(kafkaEventId))
                    .singleElement()
                    .extracting(alert -> alert.getAlertType())
                    .isEqualTo(AlertType.SPEEDING);
            assertThat(alertRepository.findByEventId(directEventId))
                    .singleElement()
                    .extracting(alert -> alert.getAlertType())
                    .isEqualTo(AlertType.SPEEDING);
            assertThat(telemetryRepository.countByEventId(directEventId)).isEqualTo(1);
            assertThat(telemetryRepository.countByEventId(kafkaEventId)).isEqualTo(1);
        } finally {
            if (renamed) {
                jdbcTemplate.execute("ALTER TABLE alert_unavailable RENAME TO alert");
            }
            if (!container.isRunning()) {
                container.start();
            }
        }
    }

    private long alertCount(String eventId) {
        try {
            return alertRepository.countByEventId(eventId);
        } catch (DataAccessException exception) {
            return 0;
        }
    }

    private TelemetryEvent event(String eventId, double speed, double temperature) {
        return new TelemetryEvent(eventId, "VH-DB", Instant.parse("2026-10-24T12:30:00Z"), speed, temperature);
    }

    private KafkaGroupSupport kafka() {
        return new KafkaGroupSupport(bootstrapServers, groupId, topic);
    }
}
