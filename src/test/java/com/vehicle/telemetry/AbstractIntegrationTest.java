package com.vehicle.telemetry;

import com.vehicle.telemetry.config.KafkaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("telemetry")
            .withUsername("telemetry")
            .withPassword("telemetry");

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.properties.session.timeout.ms", () -> "10000");
        registry.add("spring.kafka.consumer.properties.heartbeat.interval.ms", () -> "2000");
        registry.add("vehicle.simulator.enabled", () -> "false");
    }

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @BeforeEach
    void ensureListenerRunning() {
        MessageListenerContainer container = listenerRegistry.getListenerContainer(KafkaConfig.LISTENER_ID);
        if (container != null && !container.isRunning()) {
            container.start();
        }
    }

    protected MessageListenerContainer listenerContainer() {
        MessageListenerContainer container = listenerRegistry.getListenerContainer(KafkaConfig.LISTENER_ID);
        if (container == null) {
            throw new IllegalStateException("Telemetry listener is not registered");
        }
        return container;
    }
}
