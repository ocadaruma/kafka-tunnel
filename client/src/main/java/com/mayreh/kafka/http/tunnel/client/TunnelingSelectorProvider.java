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
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import sun.nio.ch.DefaultSelectorProvider;

public class TunnelingSelectorProvider extends SelectorProvider {
    private static final String TUNNEL_ENDPOINT_PROPERTY = "kafka.http.tunnel.endpoint";
    private static final String TUNNEL_TLS_PROPERTY = "kafka.http.tunnel.tls";
    private final SelectorProvider defaultProvider = DefaultSelectorProvider.create();
    private final EventLoopGroup eventLoopGroup;
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

    public TunnelingSelectorProvider() {
        eventLoopGroup = new NioEventLoopGroup(1, (Executor) null, defaultProvider);
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

        return new TunnelingSelector(this);
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
        boolean ssl = Boolean.parseBoolean(System.getProperty(TUNNEL_TLS_PROPERTY, "false"));
        int tunnelPort = Integer.parseInt(tunnelEndpoint.split(":")[1]);
        return new TunnelingSocketChannel(
                new InetSocketAddress(tunnelHost, tunnelPort),
                this,
                defaultProvider,
                eventLoopGroup,
                ssl ? SslContextBuilder
                        .forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build() : null);
    }

    // Should be used only for testing
    public static void setTunnelingCondition(BooleanSupplier shouldEnableTunneling) {
        TunnelingSelectorProvider.shouldEnableTunneling = shouldEnableTunneling;
    }
}
