package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

public class TunnelingSocketChannel extends SocketChannel {
    private final SocketChannel delegate;
    private final InetSocketAddress tunnelServer;
    private InetSocketAddress brokerAddress = null;
    private HttpSend send = null;
    private HttpReceive receive = null;

    public TunnelingSocketChannel(
            SelectorProvider provider,
            SocketChannel delegate,
            InetSocketAddress tunnelServer) {
        super(provider);
        this.delegate = delegate;
        this.tunnelServer = tunnelServer;
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        delegate.bind(local);
        return this;
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        delegate.setOption(name, value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return delegate.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return delegate.supportedOptions();
    }

    @Override
    public SocketChannel shutdownInput() throws IOException {
        return delegate.shutdownInput();
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        return delegate.shutdownOutput();
    }

    @Override
    public Socket socket() {
        return delegate.socket();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public boolean isConnectionPending() {
        return delegate.isConnectionPending();
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        brokerAddress = (InetSocketAddress) remote;
        return delegate.connect(tunnelServer);
    }

    @Override
    public boolean finishConnect() throws IOException {
        return delegate.finishConnect();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return delegate.getRemoteAddress();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (receive == null) {
            receive = new HttpReceive(delegate);
        }
        int read = receive.read(dst);
        if (!receive.hasRemaining()) {
            receive = null;
        }
        return read;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (send == null) {
            send = new HttpSend(delegate, brokerAddress);
        }
        int written = send.write(src);
        if (!send.hasRemaining()) {
            send = null;
        }
        return written;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        try {
            delegate.getClass()
                    .getDeclaredMethod("implCloseSelectableChannel")
                    .invoke(delegate);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        try {
            delegate.getClass()
                    .getDeclaredMethod("implConfigureBlocking", boolean.class)
                    .invoke(delegate, block);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
