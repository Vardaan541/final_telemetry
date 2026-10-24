package com.vehicle.telemetry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.telemetry.AbstractIntegrationTest;
import com.vehicle.telemetry.dto.TelemetryRequest;
import com.vehicle.telemetry.entity.AlertType;
import com.vehicle.telemetry.repository.AlertRepository;
import com.vehicle.telemetry.repository.TelemetryRepository;
import com.vehicle.telemetry.dto.AlertResponse;
import com.vehicle.telemetry.service.AlertStream;
import com.vehicle.telemetry.service.TelemetryProcessingService;
import com.vehicle.telemetry.dto.TelemetryEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TelemetryProcessingService processingService;

    @Autowired
    private TelemetryRepository telemetryRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertStream alertStream;

    @Test
    void requestsDoNotRequireLogin() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk());

        String eventId = "evt-anon-" + UUID.randomUUID();
        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(eventId, "VH-1", 10, 10))))
                .andExpect(status().isAccepted());
    }

    @Test
    void telemetryCanBeIngestedAndAlertsCanBeReadWithoutLogin() throws Exception {
        String eventId = "evt-user-" + UUID.randomUUID();

        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(eventId, "VH-USER", 10, 10))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        await().atMost(Duration.ofSeconds(30)).until(() -> telemetryRepository.countByEventId(eventId) == 1);
        assertThat(alertRepository.countByEventId(eventId)).isZero();

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidLoginIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void invalidTelemetryIsRejected() throws Exception {
        String eventId = "evt-bad-" + UUID.randomUUID();

        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "%s",
                                  "vehicleId": "VH-BAD",
                                  "timestamp": "2026-10-24T12:30:00Z",
                                  "speed": -5,
                                  "engineTemperature": 90
                                }
                                """.formatted(eventId)))
                .andExpect(status().isBadRequest());

        assertThat(telemetryRepository.countByEventId(eventId)).isZero();
    }

    @Test
    void dashboardIsServed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Live vehicle alerts")))
                .andExpect(content().string(containsString("/api/alerts/stream")));
    }

    @Test
    void newAlertIsPushedOnce() throws Exception {
        String eventId = "evt-live-" + UUID.randomUUID();
        CopyOnWriteArrayList<AlertResponse> received = new CopyOnWriteArrayList<>();
        try (AutoCloseable ignored = alertStream.listen(received::add)) {
            TelemetryEvent event = new TelemetryEvent(eventId, "VH-LIVE", Instant.parse("2026-10-24T12:30:00Z"), 140, 70);
            processingService.process(event);
            processingService.process(event);
        }
        assertThat(received).hasSize(1);
        assertThat(received.get(0).eventId()).isEqualTo(eventId);
        assertThat(received.get(0).alertType()).isEqualTo(AlertType.SPEEDING);
        assertThat(alertRepository.countByEventId(eventId)).isEqualTo(1);
    }

    @Test
    void adminCanFilterAndPageAlerts() throws Exception {
        String vehicleId = "VH-PAGE-" + UUID.randomUUID();
        Instant first = Instant.parse("2026-10-24T12:00:00Z");
        Instant second = Instant.parse("2026-10-24T12:05:00Z");
        Instant third = Instant.parse("2026-10-24T12:10:00Z");

        processingService.process(new TelemetryEvent("evt-p1-" + vehicleId, vehicleId, first, 130, 70));
        processingService.process(new TelemetryEvent("evt-p2-" + vehicleId, vehicleId, second, 70, 130));
        processingService.process(new TelemetryEvent("evt-p3-" + vehicleId, vehicleId, third, 140, 70));

        mockMvc.perform(get("/api/alerts")
                        .param("vehicleId", vehicleId)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].timestamp").value(third.toString()));

        mockMvc.perform(get("/api/alerts")
                        .param("vehicleId", vehicleId)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/alerts")
                        .param("vehicleId", vehicleId)
                        .param("alertType", "SPEEDING")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].alertType").value(AlertType.SPEEDING.name()));

        mockMvc.perform(get("/api/alerts")
                        .param("vehicleId", vehicleId)
                        .param("alertType", "ENGINE_OVERHEATING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].alertType").value("ENGINE_OVERHEATING"));

        mockMvc.perform(get("/api/alerts")
                        .param("vehicleId", vehicleId)
                        .param("from", second.toString())
                        .param("to", second.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].alertType").value("ENGINE_OVERHEATING"));

        mockMvc.perform(get("/api/alerts")
                        .param("vehicleId", "VH-NONE-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private TelemetryRequest request(String eventId, String vehicleId, double speed, double temperature) {
        return new TelemetryRequest(eventId, vehicleId, Instant.parse("2026-10-24T12:30:00Z"), speed, temperature);
    }
}
