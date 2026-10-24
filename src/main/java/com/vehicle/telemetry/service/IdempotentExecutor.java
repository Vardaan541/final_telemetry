package com.vehicle.telemetry.service;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;

@Component
public class IdempotentExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentExecutor.class);

    private static final String UNIQUE_VIOLATION = "23505";

    private final PlatformTransactionManager transactionManager;

    public IdempotentExecutor(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Runs one persistence operation in its own transaction.
     * PostgreSQL aborts the current transaction on a unique violation, so each write
     * is isolated. Only SQL state 23505 is treated as a duplicate; every other failure
     * is rethrown so the Kafka offset stays uncommitted.
     *
     * @return false when the write was rejected as a duplicate
     */
    public boolean run(Runnable action, String duplicateMessage) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.executeWithoutResult(status -> action.run());
            return true;
        } catch (RuntimeException exception) {
            if (isUniqueViolation(exception)) {
                log.info(duplicateMessage);
                return false;
            }
            throw exception;
        }
    }

    private boolean isUniqueViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && constraintViolation.getSQLException() != null
                    && UNIQUE_VIOLATION.equals(constraintViolation.getSQLException().getSQLState())) {
                return true;
            }
            if (current instanceof SQLException sqlException && UNIQUE_VIOLATION.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
