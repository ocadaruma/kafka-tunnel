package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.function.BooleanSupplier;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import sun.nio.ch.DefaultSelectorProvider;

/**
 * A {@link SelectorProvider} that provides a family of nio classes to
 * tunnels Kafka protocol through a single HTTP endpoint.
 * <p>
 * This class is expected to be used to replace the default {@link SelectorProvider}
 * in the JVM by setting the system property {@code java.nio.channels.spi.SelectorProvider}.
 * <p>
 * Since multiple SelectorProvider cannot be configured in the JVM, this class only
 * provides tunneling capabilities when the calling class is {@code org.apache.kafka.common.network.Selector},
 * otherwise it delegates to the default {@link DefaultSelectorProvider}.
 */
public class TunnelingSelectorProvider extends SelectorProvider {
    private static final String TUNNEL_ENDPOINT_PROPERTY = "kafka.http.tunnel.endpoint";
    private static final String TUNNEL_TLS_PROPERTY = "kafka.http.tunnel.tls";
    private final SelectorProvider defaultProvider = DefaultSelectorProvider.create();
    private static volatile BooleanSupplier shouldEnableTunneling;
    static {
        shouldEnableTunneling = () -> {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                if ("org.apache.kafka.common.network.Selector".equals(element.getClassName())) {
                    return true;
                }
            }
            return false;
        };
    }

    @Override
    public DatagramChannel openDatagramChannel() throws IOException {
        return defaultProvider.openDatagramChannel();
    }

    @Override
    public DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
        return defaultProvider.openDatagramChannel(family);
    }

    @Override
    public Pipe openPipe() throws IOException {
        return defaultProvider.openPipe();
    }

    @Override
    public AbstractSelector openSelector() throws IOException {
        if (!shouldEnableTunneling.getAsBoolean()) {
            return defaultProvider.openSelector();
        }
        boolean ssl = Boolean.parseBoolean(System.getProperty(TUNNEL_TLS_PROPERTY, "false"));
        return new TunnelingSelector(
                this,
                defaultProvider,
                ssl ? SslContextBuilder
                        .forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build() : null);
    }

    @Override
    public ServerSocketChannel openServerSocketChannel() throws IOException {
        return defaultProvider.openServerSocketChannel();
    }

    @Override
    public SocketChannel openSocketChannel() throws IOException {
        if (!shouldEnableTunneling.getAsBoolean()) {
            return defaultProvider.openSocketChannel();
        }

        String tunnelEndpoint = System.getProperty(TUNNEL_ENDPOINT_PROPERTY);
        String tunnelHost = tunnelEndpoint.split(":")[0];
        int tunnelPort = Integer.parseInt(tunnelEndpoint.split(":")[1]);
        return new TunnelingSocketChannel(
                new InetSocketAddress(tunnelHost, tunnelPort),
                this,
                defaultProvider);
    }

    // Should be used only for testing
    public static void setTunnelingCondition(BooleanSupplier shouldEnableTunneling) {
        TunnelingSelectorProvider.shouldEnableTunneling = shouldEnableTunneling;
    }
}
