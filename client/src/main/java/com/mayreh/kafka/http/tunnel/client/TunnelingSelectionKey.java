package com.mayreh.kafka.http.tunnel.client;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelectionKey;

import com.mayreh.kafka.http.tunnel.client.TunnelingSocketChannel2.ChannelState;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
public class TunnelingSelectionKey extends AbstractSelectionKey {
    private int interestOps = 0;

    private final Selector selector;
    private final TunnelingSocketChannel2 channel;

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
        int ops = 0;
        if (channel.state().contains(ChannelState.Connectable)) {
            ops |= OP_CONNECT;
        }
        if (channel.state().contains(ChannelState.Readable)) {
            ops |= OP_READ;
        }
        if (channel.state().contains(ChannelState.Writable)) {
            ops |= OP_WRITE;
        }
        return ops & interestOps;
    }
}
