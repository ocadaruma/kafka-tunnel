package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TunnelingSelector extends AbstractSelector {
    private final AbstractSelector delegate;
    private final Map<SelectionKey, TunnelingSelectionKey> keyLookupMap;

    public TunnelingSelector(SelectorProvider provider, AbstractSelector delegate) {
        super(provider);
        this.delegate = delegate;
        keyLookupMap = new HashMap<>();
    }

    @Override
    protected void implCloseSelector() throws IOException {
        ReflectionUtil.call(delegate, "implCloseSelector");
    }

    @Override
    protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
        if (!(ch instanceof TunnelingSocketChannel)) {
            throw new IllegalArgumentException("channel must be TunnelingSocketChannel");
        }
        SelectionKey key = (SelectionKey) ReflectionUtil.call(
                delegate,
                "register",
                new Class<?>[] { AbstractSelectableChannel.class, int.class, Object.class },
                ((TunnelingSocketChannel) ch).delegate(), ops, att);
        ReflectionUtil.call(
                ((TunnelingSocketChannel) ch).delegate(),
                "addKey",
                new Class<?>[] { SelectionKey.class },
                key);
        synchronized (this) {
            return keyLookupMap.computeIfAbsent(key, k -> new TunnelingSelectionKey(this, key, ch));
        }
    }

    @Override
    public Set<SelectionKey> keys() {
        return new SelectionKeySet(delegate.keys(), keyLookupMap);
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return new SelectionKeySet(delegate.selectedKeys(), keyLookupMap);
    }

    @Override
    public int selectNow() throws IOException {
        return delegate.selectNow();
    }

    @Override
    public int select(long timeout) throws IOException {
        return delegate.select(timeout);
    }

    @Override
    public int select() throws IOException {
        return delegate.select();
    }

    @Override
    public Selector wakeup() {
        delegate.wakeup();
        return this;
    }
}
