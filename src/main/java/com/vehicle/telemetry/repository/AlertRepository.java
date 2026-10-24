package com.vehicle.telemetry.repository;

import com.vehicle.telemetry.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    List<Alert> findByEventId(String eventId);

    long countByEventId(String eventId);
}
