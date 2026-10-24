package com.vehicle.telemetry.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.telemetry.AbstractIntegrationTest;
import com.vehicle.telemetry.KafkaGroupSupport;
import com.vehicle.telemetry.dto.TelemetryRequest;
import com.vehicle.telemetry.entity.AlertType;
import com.vehicle.telemetry.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelemetryPipelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertRepository alertRepository;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${vehicle.kafka.topic}")
    private String topic;

    @Test
    void publishedTelemetryIsConsumedAndDuplicatePublishDoesNotDuplicateTheAlert() throws Exception {
        String eventId = "evt-pipe-" + UUID.randomUUID();
        TelemetryRequest request = new TelemetryRequest(
                eventId,
                "VH-PIPE",
                Instant.parse("2026-10-24T12:30:00Z"),
                151.0,
                40.0
        );
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        await().atMost(Duration.ofSeconds(30)).until(() -> alertRepository.countByEventId(eventId) == 1);
        KafkaGroupSupport kafka = new KafkaGroupSupport(bootstrapServers, groupId, topic);
        long committed = kafka.committedOffset();

        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(30)).until(() -> kafka.committedOffset() >= committed + 1);
        assertThat(alertRepository.findByEventId(eventId))
                .singleElement()
                .extracting(alert -> alert.getAlertType())
                .isEqualTo(AlertType.SPEEDING);
    }
}
