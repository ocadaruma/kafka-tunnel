package com.mayreh.kafka.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.utils.Time;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.CoreUtils;
import kafka.utils.TestUtils;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import scala.Option;

/**
 * Starts embedded Kafka brokers in a same process on random ports
 */
@Slf4j
public class EmbeddedKafkaCluster implements AutoCloseable {
    private final List<KafkaServer> servers;
    @Getter
    @Accessors(fluent = true)
    private final String bootstrapServers;

    public EmbeddedKafkaCluster(int numBrokers, String zkConnect) {
        this(numBrokers, zkConnect, new Properties());
    }

    public EmbeddedKafkaCluster(int numBrokers,
                                String zkConnect,
                                Properties brokerProperties) {
        servers = new ArrayList<>(numBrokers);
        List<String> listeners = new ArrayList<>(numBrokers);

        for (int i = 0; i < numBrokers; i++) {
            Properties prop = createBrokerConfig(i, zkConnect);
            prop.putAll(brokerProperties);
            KafkaServer server = TestUtils.createServer(KafkaConfig.fromProps(prop), Time.SYSTEM);
            int port = TestUtils.boundPort(server, SecurityProtocol.PLAINTEXT);
            String listener = "127.0.0.1:" + port;
            listeners.add(listener);
            servers.add(server);

            log.info("Broker {} started at {}", i, listener);
        }

        bootstrapServers = String.join(",", listeners);
    }

    private static Properties createBrokerConfig(int brokerId, String zkConnect) {
        return TestUtils.createBrokerConfig(
                brokerId, zkConnect,
                false, // disable controlled shutdown
                true, // enable delete topics
                0, // use random port
                Option.empty(), // interBrokerSecurityProtocol
                Option.empty(), // trustStoreFile
                Option.empty(), // saslProperties
                true, // enablePlaintext
                false, // enableSaslPlaintext
                0, // saslPlaintextPort
                false, // enableSsl,
                0, // sslPort,
                false, // enableSaslSsl
                0, // sasslSslPort
                Option.empty(), // omit rack information
                1, // logDir count
                false, // disable delegation token
                1, // num partitions
                (short) 1, // default replication factor
                false // enableFetchFromFollower
        );
    }

    @Override
    public void close() {
        for (KafkaServer server : servers) {
            try {
                server.shutdown();
                server.awaitShutdown();
            } catch (Exception e) {
                log.warn("Kafka broker {} threw an exception during shutting down", server.config().brokerId(), e);
            }

            try {
                CoreUtils.delete(server.config().logDirs());
            } catch (Exception e) {
                log.warn("Failed to delete log dirs {}", server.config().logDirs(), e);
            }
        }
    }
}
