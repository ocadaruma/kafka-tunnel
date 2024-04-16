package com.mayreh.kafka.http.tunnel.client;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectionKey;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TunnelingSelectionKey extends AbstractSelectionKey {
    private int interestOps = 0;

    private final Selector selector;
    private final TunnelingSocketChannel channel;

    @Override
    public SelectableChannel channel() {
        return channel;
    }

    @Override
    public Selector selector() {
        return selector;
    }

    @Override
    public int interestOps() {
        return interestOps;
    }

    @Override
    public SelectionKey interestOps(int ops) {
        interestOps = ops;
        return this;
    }

    @Override
    public int readyOps() {
        return channel.readyOps() & interestOps;
    }
}
