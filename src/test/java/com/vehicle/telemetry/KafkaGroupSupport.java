package com.vehicle.telemetry;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class KafkaGroupSupport {

    private final String bootstrapServers;

    private final String groupId;

    private final String topic;

    public KafkaGroupSupport(String bootstrapServers, String groupId, String topic) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.topic = topic;
    }

    public long endOffset() throws Exception {
        TopicPartition partition = partition();
        try (AdminClient admin = admin()) {
            ListOffsetsResult result = admin.listOffsets(Map.of(partition, OffsetSpec.latest()));
            return result.partitionResult(partition).get(10, TimeUnit.SECONDS).offset();
        }
    }

    public long committedOffset() throws Exception {
        TopicPartition partition = partition();
        try (AdminClient admin = admin()) {
            OffsetAndMetadata metadata = admin.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS)
                    .get(partition);
            return metadata == null ? -1L : metadata.offset();
        }
    }

    public boolean hasMembers() {
        try (AdminClient admin = admin()) {
            return !admin.describeConsumerGroups(List.of(groupId))
                    .all()
                    .get(10, TimeUnit.SECONDS)
                    .get(groupId)
                    .members()
                    .isEmpty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return true;
        } catch (ExecutionException | TimeoutException exception) {
            return true;
        }
    }

    public void resetOffsetsToZero() throws Exception {
        TopicPartition partition = partition();
        try (AdminClient admin = admin()) {
            admin.alterConsumerGroupOffsets(groupId, Map.of(partition, new OffsetAndMetadata(0L)))
                    .all()
                    .get(30, TimeUnit.SECONDS);
        }
    }

    private TopicPartition partition() {
        return new TopicPartition(topic, 0);
    }

    private AdminClient admin() {
        return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }
}
