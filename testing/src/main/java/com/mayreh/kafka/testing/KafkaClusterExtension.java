package com.mayreh.kafka.testing;

import java.util.Properties;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * JUnit extension that starts an embedded Kafka cluster.
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaClusterExtension implements BeforeAllCallback, AfterAllCallback {
    private static final int KAFKA_CLUSTER_SIZE = 3;

    private EmbeddedZooKeeper zooKeeper;
    private EmbeddedKafkaCluster kafkaCluster;
    @Getter
    @Accessors(fluent = true)
    private KafkaAdmin admin;
    private final int clusterSize;
    private final Properties brokerProperties;

    public KafkaClusterExtension() {
        this(KAFKA_CLUSTER_SIZE, new Properties());
    }

    public String bootstrapServers() {
        return kafkaCluster.bootstrapServers();
    }

    private static void safeClose(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception e) {
            log.warn("Failed to close the resource", e);
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        zooKeeper = new EmbeddedZooKeeper();
        kafkaCluster = new EmbeddedKafkaCluster(clusterSize,
                                                zooKeeper.zkConnectAsString(),
                                                brokerProperties);
        admin = new KafkaAdmin(kafkaCluster.bootstrapServers());
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        safeClose(admin);
        safeClose(kafkaCluster);
        safeClose(zooKeeper);
    }
}
