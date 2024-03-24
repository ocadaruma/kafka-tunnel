package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CleartextTransportLayer implements TransportLayer {
    private final SocketChannel delegate;

    @Override
    public void setSelectionKey(SelectionKey key) {
        // no-op
    }

    @Override
    public boolean hasBufferedRead() {
        return false;
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public void handshake() throws IOException {
        // no-op
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return delegate.write(srcs, offset, length);
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        return delegate.write(srcs);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        return delegate.read(dsts, offset, length);
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return delegate.read(dsts);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return delegate.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return delegate.write(src);
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
