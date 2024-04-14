package com.mayreh.kafka.http.tunnel.client;

import static com.mayreh.kafka.http.tunnel.client.Utils.unchecked;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;

import com.mayreh.kafka.http.tunnel.client.Utils.IORunnable;
import com.mayreh.kafka.http.tunnel.client.Utils.IOSupplier;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SocketAdaptor extends Socket {
    private final TunnelingSocketChannel2 channel;

    @Override
    public InetAddress getInetAddress() {
        return ((InetSocketAddress) unchecked(channel::getRemoteAddress)).getAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return ((InetSocketAddress) unchecked(channel::getLocalAddress)).getAddress();
    }

    @Override
    public int getPort() {
        return ((InetSocketAddress) unchecked(channel::getRemoteAddress)).getPort();
    }

    @Override
    public int getLocalPort() {
        return ((InetSocketAddress) unchecked(channel::getLocalAddress)).getPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return unchecked(channel::getRemoteAddress);
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return unchecked(channel::getLocalAddress);
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        wrap(() -> {
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, on);
        });
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return wrap(() -> channel.getOption(StandardSocketOptions.SO_KEEPALIVE));
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        wrap(() -> {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, on);
        });
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return wrap(() ->  channel.getOption(StandardSocketOptions.TCP_NODELAY));
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        wrap(() -> {
            channel.setOption(StandardSocketOptions.SO_LINGER, linger);
        });
    }

    @Override
    public int getSoLinger() throws SocketException {
        return wrap(() -> channel.getOption(StandardSocketOptions.SO_LINGER));
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        wrap(() -> {
            channel.setOption(SO_TIMEOUT, timeout);
        });
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
        return wrap(() -> channel.getOption(SO_TIMEOUT));
    }

    @Override
    public synchronized void setSendBufferSize(int size) throws SocketException {
        wrap(() -> {
            channel.setOption(StandardSocketOptions.SO_SNDBUF, size);
        });
    }

    @Override
    public synchronized int getSendBufferSize() throws SocketException {
        return wrap(() -> channel.getOption(StandardSocketOptions.SO_SNDBUF));
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        wrap(() -> {
            channel.setOption(StandardSocketOptions.SO_RCVBUF, size);
        });
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        return wrap(() -> channel.getOption(StandardSocketOptions.SO_RCVBUF));
    }

    @Override
    public void close() {
        // noop
    }

    private static <T> T wrap(IOSupplier<T> supplier) throws SocketException {
        try {
            return supplier.get();
        } catch (IOException e) {
            throw new SocketException(e.getMessage());
        }
    }

    private static void wrap(IORunnable runnable) throws SocketException {
        try {
            runnable.run();
        } catch (IOException e) {
            throw new SocketException(e.getMessage());
        }
    }

    private static SocketOption<Integer> SO_TIMEOUT = new SocketOption<Integer>() {
        @Override
        public String name() {
            return "SO_TIMEOUT";
        }

        @Override
        public Class<Integer> type() {
            return Integer.class;
        }
    };
}
