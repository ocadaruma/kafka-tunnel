package com.mayreh.kafka.http.tunnel.server;

import static java.util.Collections.synchronizedSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.Serdes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.mayreh.kafka.http.tunnel.client.TunnelingSelectorProvider;
import com.mayreh.kafka.testing.KafkaClusterExtension;

import com.linecorp.armeria.client.WebClient;

public class TunnelingTest {
    static {
        TunnelingSelectorProvider.setTunnelingCondition(
                () -> {
                    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                    for (StackTraceElement element : stackTrace) {
                        if (element.getMethodName().startsWith("integrationTest_")) {
                            return true;
                        }
                    }
                    return Thread.currentThread().getName().contains("test-client-producer");
                });
    }

    @RegisterExtension
    public static final KafkaClusterExtension rule = new KafkaClusterExtension();

    private String topic;
    private TunnelingServer server;

    @BeforeEach
    public void setUp() throws Exception {
//        Path profilerPath = Paths.get("/Users/hokada/develop/tools/async-profiler-2.8.3-macos/profiler.sh");
//        long pid = ProcessHandle.current().pid();
//        ProcessBuilder pb = new ProcessBuilder(
//                profilerPath.toString(), "-f", "/tmp/profile-" + pid + ".jfr", "-d", "120", "-e", "wall", String.valueOf(pid));
//        pb.start();

        topic = rule.admin().createRandomTopic(3, 3);
        server = new TunnelingServer(serverBuilder -> {
            serverBuilder
                    .tlsSelfSigned()
                    .https(0);
//                    .http(0);
        });
        System.setProperty("kafka.http.tunnel.endpoint", "localhost:" + server.httpsPort());
        System.setProperty("kafka.http.tunnel.tls", "true");
    }

    @AfterEach
    public void tearDown() {
        rule.admin().deleteTopics(true, topic);
        server.close();
    }

    @Test
    public void integrationTest_MessageDelivery() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, rule.bootstrapServers());
        props.setProperty(ProducerConfig.CLIENT_ID_CONFIG, "test-client-producer");
        // TODO: support multiple in-flight requests
        props.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1");
        props.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, "0");
        props.setProperty(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        int recordCount = 100;
        Set<Integer> produced = synchronizedSet(new HashSet<>());
        try (Producer<String, String> producer = new KafkaProducer<>(
                props, Serdes.String().serializer(), Serdes.String().serializer())) {
            List<PartitionInfo> info = producer.partitionsFor(topic);

            assertEquals(3, info.size());

            for (int i = 0; i < recordCount; i++) {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic, null, String.valueOf(i));
                int value = i;
                producer.send(record, (m, e) -> {
                    if (e == null) {
                        produced.add(value);
                    }
                });
            }
        }
        assertEquals(recordCount, produced.size());
    }
}
