package com.vehicle.telemetry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.telemetry.dto.AlertResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class AlertStream {

    private static final Logger log = LoggerFactory.getLogger(AlertStream.class);

    private final ObjectMapper objectMapper;

    private final List<Consumer<AlertResponse>> listeners = new CopyOnWriteArrayList<>();

    private final List<LiveConnection> connections = new CopyOnWriteArrayList<>();

    public AlertStream(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void publish(AlertResponse alert) {
        for (Consumer<AlertResponse> listener : listeners) {
            try {
                listener.accept(alert);
            } catch (RuntimeException exception) {
                log.warn("alert listener failed eventId={}", alert.eventId(), exception);
            }
        }
    }

    public AutoCloseable listen(Consumer<AlertResponse> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public SseEmitter openStream() {
        LiveConnection connection = new LiveConnection();
        connections.add(connection);
        listeners.add(connection);
        Runnable detach = () -> {
            connections.remove(connection);
            listeners.remove(connection);
        };
        connection.emitter.onCompletion(detach);
        connection.emitter.onTimeout(detach);
        connection.emitter.onError(error -> detach.run());
        return connection.emitter;
    }

    @Scheduled(fixedRate = 15_000, initialDelay = 15_000)
    public void heartbeat() {
        for (LiveConnection connection : connections) {
            connection.comment("ping");
        }
    }

    private final class LiveConnection implements Consumer<AlertResponse> {

        private final SseEmitter emitter = new SseEmitter(0L);

        @Override
        public void accept(AlertResponse alert) {
            try {
                send(SseEmitter.event().name("alert").data(objectMapper.writeValueAsString(alert)));
            } catch (JsonProcessingException exception) {
                log.error("alert payload could not be written eventId={}", alert.eventId(), exception);
            } catch (IllegalStateException exception) {
                log.debug("alert stream is not open yet eventId={}", alert.eventId());
            } catch (Exception exception) {
                log.info("alert stream closed eventId={}", alert.eventId());
                emitter.complete();
            }
        }

        private void comment(String value) {
            try {
                send(SseEmitter.event().comment(value));
            } catch (Exception exception) {
                emitter.complete();
            }
        }

        private void send(SseEmitter.SseEventBuilder event) throws java.io.IOException {
            synchronized (emitter) {
                emitter.send(event);
            }
        }
    }
}
