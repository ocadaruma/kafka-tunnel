package com.mayreh.kafka.http.tunnel.client;

import java.nio.channels.SelectionKey;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SelectionKeySet extends AbstractSet<SelectionKey> {
    private final Set<SelectionKey> delegate;
    private final Map<SelectionKey, TunnelingSelectionKey> selectionKeyMap;

    @Override
    public Iterator<SelectionKey> iterator() {
        Iterator<SelectionKey> iterator = delegate.iterator();
        return new Iterator<SelectionKey>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public SelectionKey next() {
                TunnelingSelectionKey key = selectionKeyMap.get(iterator.next());
                if (key == null) {
                    throw new IllegalStateException("Should be a bug");
                }
                return key;
            }

            @Override
            public void remove() {
                iterator.remove();
            }
        };
    }

    @Override
    public int size() {
        return delegate.size();
    }
}
