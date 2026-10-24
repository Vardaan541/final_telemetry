package com.vehicle.telemetry.repository;

import com.vehicle.telemetry.entity.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {

    Optional<Telemetry> findByEventId(String eventId);

    long countByEventId(String eventId);
}
