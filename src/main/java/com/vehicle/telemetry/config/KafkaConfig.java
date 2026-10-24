package com.vehicle.telemetry.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String LISTENER_ID = "vehicle-telemetry-listener";

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public NewTopic vehicleTelemetryTopic(@Value("${vehicle.kafka.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    /**
     * Retries failed records without committing the offset. A database outage therefore
     * leaves the event available for processing after PostgreSQL recovers.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "processing failure topic={} partition={} offset={} key={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        exception),
                new FixedBackOff(2_000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setCommitRecovered(false);
        handler.setAckAfterHandle(false);
        return handler;
    }
}
