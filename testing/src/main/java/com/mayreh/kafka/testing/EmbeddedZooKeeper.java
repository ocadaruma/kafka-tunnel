package com.mayreh.kafka.testing;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;

import org.apache.kafka.common.utils.Utils;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;

import lombok.extern.slf4j.Slf4j;

/**
 * Starts embedded ZooKeeper server in a same process on random port
 */
@Slf4j
public class EmbeddedZooKeeper implements AutoCloseable {
    private final int port;
    private final ServerCnxnFactory cnxnFactory;
    private final File snapshotDir;
    private final File logDir;

    public EmbeddedZooKeeper() {
        try {
            snapshotDir = Files.createTempDirectory("zookeeper-snapshot").toFile();
            logDir = Files.createTempDirectory("zookeeper-logs").toFile();
            ZooKeeperServer zkServer = new ZooKeeperServer(snapshotDir,
                                                           logDir,
                                                           ZooKeeperServer.DEFAULT_TICK_TIME);

            InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 0);
            // disable max client cnxns by passing 0
            cnxnFactory = ServerCnxnFactory.createFactory(addr, 0);
            cnxnFactory.startup(zkServer);

            port = zkServer.getClientPort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String zkConnectAsString() {
        return "127.0.0.1:" + port;
    }

    @Override
    public void close() {
        cnxnFactory.shutdown();

        safeDelete(snapshotDir);
        safeDelete(logDir);
    }

    private static void safeDelete(File file) {
        try {
            Utils.delete(file);
        } catch (IOException e) {
            log.warn("Failed to delete {}", file, e);
        }
    }
}
