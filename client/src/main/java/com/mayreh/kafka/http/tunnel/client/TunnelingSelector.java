package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TunnelingSelector extends AbstractSelector {
    private final ReentrantLock selectLock = new ReentrantLock();
    private final Condition waiter = selectLock.newCondition();
    private final Set<SelectionKey> keys = new HashSet<>();
    private final Set<SelectionKey> selectedKeys = new HashSet<>();
    private final EventLoopGroup eventLoopGroup;
    private final SslContext sslContext;

    public TunnelingSelector(
            SelectorProvider provider,
            SelectorProvider defaultProvider,
            SslContext sslContext) {
        super(provider);
        this.sslContext = sslContext;
        eventLoopGroup = new NioEventLoopGroup(1, (Executor) null, defaultProvider);
    }

    @Override
    protected void implCloseSelector() throws IOException {
        eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
    }

    @Override
    protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
        TunnelingSocketChannel channel = (TunnelingSocketChannel) ch;
        channel.register(sslContext, eventLoopGroup);
        TunnelingSelectionKey key = new TunnelingSelectionKey(this, channel);
        key.interestOps(ops);
        key.attach(att);
        keys.add(key);
        channel.addSelectionKey(key);
        return key;
    }

    @Override
    public Set<SelectionKey> keys() {
        return Collections.unmodifiableSet(keys);
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return Utils.ungrowableSet(selectedKeys);
    }

    @Override
    public int selectNow() throws IOException {
        return select(0);
    }

    @Override
    public int select(long timeout) throws IOException {
        selectLock.lock();
        try {
            keys.removeAll(cancelledKeys());
            selectedKeys.clear();
            for (SelectionKey key : keys) {
                TunnelingSelectionKey tunnelingKey = (TunnelingSelectionKey) key;
                int readyOps = tunnelingKey.readyOps();
                if (readyOps != 0) {
                    selectedKeys.add(key);
                }
            }
            if (!selectedKeys.isEmpty()) {
                return selectedKeys.size();
            }

            waiter.await(timeout, TimeUnit.MILLISECONDS);

            for (SelectionKey key : keys) {
                TunnelingSelectionKey tunnelingKey = (TunnelingSelectionKey) key;
                int readyOps = tunnelingKey.readyOps();
                if (readyOps != 0) {
                    selectedKeys.add(key);
                }
            }
            return selectedKeys.size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } finally {
            selectLock.unlock();
        }
    }

    @Override
    public int select() throws IOException {
        return select(Long.MAX_VALUE);
    }

    @Override
    public Selector wakeup() {
        selectLock.lock();
        try {
            waiter.signalAll();
        } finally {
            selectLock.unlock();
        }
        return this;
    }
}
