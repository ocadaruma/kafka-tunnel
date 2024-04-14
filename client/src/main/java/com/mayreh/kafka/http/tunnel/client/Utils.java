package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class Utils {
    public interface IOSupplier<T> {
        T get() throws IOException;
    }

    public interface IORunnable {
        void run() throws IOException;
    }

    public static <T> T unchecked(IOSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void unchecked(IORunnable runnable) {
        try {
            runnable.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> Set<T> ungrowableSet(Set<T> underlying) {
        return new Set<T>() {
            @Override
            public int size() { return underlying.size(); }

            @Override
            public boolean isEmpty() { return underlying.isEmpty(); }

            @Override
            public boolean contains(Object o) { return underlying.contains(o); }

            @Override
            public Iterator<T> iterator() { return underlying.iterator(); }

            @Override
            public Object[] toArray() { return underlying.toArray(); }

            @Override
            public <T1> T1[] toArray(T1[] a) { return underlying.toArray(a); }

            @Override
            public boolean add(T t) { throw new UnsupportedOperationException(); }

            @Override
            public boolean remove(Object o) { return underlying.remove(o); }

            @Override
            public boolean containsAll(Collection<?> c) { return underlying.containsAll(c); }

            @Override
            public boolean addAll(Collection<? extends T> c) { throw new UnsupportedOperationException(); }

            @Override
            public boolean retainAll(Collection<?> c) { return underlying.retainAll(c); }

            @Override
            public boolean removeAll(Collection<?> c) { return underlying.removeAll(c); }

            @Override
            public void clear() { underlying.clear(); }
        };
    }
}
