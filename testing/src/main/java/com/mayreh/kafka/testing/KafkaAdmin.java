package com.mayreh.kafka.testing;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import lombok.extern.slf4j.Slf4j;

/**
 * A wrapper of {@link AdminClient} for providing easy operation to manage topics
 */
@Slf4j
public class KafkaAdmin implements AutoCloseable {
    private final AdminClient adminClient;

    public KafkaAdmin(String bootstrapServers) {
        Properties props = new Properties();
        props.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.setProperty(AdminClientConfig.CLIENT_ID_CONFIG, "admin");

        adminClient = AdminClient.create(props);
    }

    /**
     * Create a topic with random name
     * @return created topic name
     */
    public String createRandomTopic(int numPartitions, int replicationFactor) {
        String topicName = "test-" + UUID.randomUUID();
        createTopic(topicName, numPartitions, replicationFactor);
        return topicName;
    }

    public void createTopic(String topicName, int numPartitions, int replicationFactor) {
        NewTopic newTopic = new NewTopic(topicName, numPartitions, (short) replicationFactor);

        try {
            adminClient.createTopics(Collections.singleton(newTopic)).all().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteTopics(boolean ignoreFailures, String... topicNames) {
        try {
            adminClient.deleteTopics(Arrays.asList(topicNames)).all().get();
        } catch (Exception e) {
            if (ignoreFailures) {
                log.info("Failure while deleting topics {}, ignoring", topicNames, e);
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() {
        adminClient.close();
    }
}
