package com.vehicle.telemetry.controller;

import com.vehicle.telemetry.exception.GlobalExceptionHandler;
import com.vehicle.telemetry.exception.KafkaPublishException;
import com.vehicle.telemetry.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TelemetryController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TelemetryValidationTest {

    private static final String VALID_BODY = """
            {
              "eventId": "evt-1001",
              "vehicleId": "VH-001",
              "timestamp": "2026-10-24T12:30:00Z",
              "speed": 125.5,
              "engineTemperature": 105.0
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelemetryService telemetryService;

    @Test
    void validTelemetryIsAccepted() throws Exception {
        when(telemetryService.ingest(any())).thenReturn(
                new com.vehicle.telemetry.dto.TelemetryAcceptedResponse("evt-1001", "PUBLISHED"));

        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("evt-1001"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(telemetryService).ingest(any());
    }

    @Test
    void negativeSpeedIsRejected() throws Exception {
        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("125.5", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("speed cannot be negative")));

        verify(telemetryService, never()).ingest(any());
    }

    @Test
    void missingEventIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": "VH-001",
                                  "timestamp": "2026-10-24T12:30:00Z",
                                  "speed": 80,
                                  "engineTemperature": 90
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("eventId")));

        verify(telemetryService, never()).ingest(any());
    }

    @Test
    void missingVehicleIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-1001",
                                  "timestamp": "2026-10-24T12:30:00Z",
                                  "speed": 80,
                                  "engineTemperature": 90
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("vehicleId")));

        verify(telemetryService, never()).ingest(any());
    }

    @Test
    void malformedTelemetryIsRejected() throws Exception {
        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));

        verify(telemetryService, never()).ingest(any());
    }

    @Test
    void kafkaPublishFailureReturnsServiceUnavailableWithoutStackTrace() throws Exception {
        when(telemetryService.ingest(any())).thenThrow(new KafkaPublishException("broker down", new RuntimeException("stack")));

        mockMvc.perform(post("/api/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Failed to publish telemetry event"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("stack"))));
    }
}
