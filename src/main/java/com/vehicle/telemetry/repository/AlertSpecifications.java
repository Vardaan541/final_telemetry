package com.vehicle.telemetry.repository;

import com.vehicle.telemetry.entity.Alert;
import com.vehicle.telemetry.entity.AlertType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;

public final class AlertSpecifications {

    private AlertSpecifications() {
    }

    public static Specification<Alert> filter(String vehicleId, AlertType alertType, Instant from, Instant to) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (vehicleId != null) {
                predicates.add(criteriaBuilder.equal(root.get("vehicleId"), vehicleId));
            }
            if (alertType != null) {
                predicates.add(criteriaBuilder.equal(root.get("alertType"), alertType));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            if (predicates.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
