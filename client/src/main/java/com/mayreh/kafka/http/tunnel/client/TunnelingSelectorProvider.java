package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

public class TunnelingSelectorProvider extends SelectorProvider {
    private final SelectorProvider defaultProvider = sun.nio.ch.DefaultSelectorProvider.create();

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
        return defaultProvider.openSelector();
    }

    @Override
    public ServerSocketChannel openServerSocketChannel() throws IOException {
        return defaultProvider.openServerSocketChannel();
    }

    @Override
    public SocketChannel openSocketChannel() throws IOException {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().equals("org.apache.kafka.common.network.Selector")) {
                SocketChannel delegate = defaultProvider.openSocketChannel();
                return new TunnelingSocketChannel(this, delegate);
            }
        }
        return defaultProvider.openSocketChannel();
    }
}
