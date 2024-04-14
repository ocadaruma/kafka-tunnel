package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TunnelingSocketChannel2 extends SocketChannel {
    private Channel channel;
    private InetSocketAddress brokerAddress;
    private ByteBuffer writeBuffer;
    private final InetSocketAddress tunnelServer;
    private final Bootstrap bootstrap;
    private final SocketAdaptor socket;
    private final Set<TunnelingSelectionKey> keys = ConcurrentHashMap.newKeySet();
    private final Deque<ByteBuffer> readBuffer = new ArrayDeque<>();
    private final Deque<Throwable> errors = new ArrayDeque<>();
    private final ReentrantLock ioLock = new ReentrantLock();

    public TunnelingSocketChannel2(
            InetSocketAddress tunnelServer,
            SelectorProvider provider,
            EventLoopGroup eventLoopGroup,
            SslContext sslContext) {
        super(provider);
        this.tunnelServer = tunnelServer;
        bootstrap = new Bootstrap();
        bootstrap
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        if (sslContext != null) {
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                        }
                        ch.pipeline().addLast(
                                new HttpClientCodec(),
                                new HttpContentDecompressor(),
                                new HttpObjectAggregator(Integer.MAX_VALUE),
                                new SimpleChannelInboundHandler<FullHttpResponse>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg)
                                            throws Exception {
                                        ByteBuffer buffer = ByteBuffer.allocate(msg.content().readableBytes());
                                        msg.content().readBytes(buffer);
                                        ioLock.lock();
                                        try {
                                            readBuffer.add(buffer);
                                            state.add(ChannelState.Readable);
                                            keys.forEach(key -> {
                                                if ((key.interestOps() & SelectionKey.OP_READ) != 0) {
                                                    key.selector().wakeup();
                                                }
                                            });
                                        } finally {
                                            ioLock.unlock();
                                        }
                                    }
                                });
                    }
                });
        // Since actual write operation to the socket is done by Netty and we just
        // buffer written bytes in memory here, we always consider the channel writable
        state.add(ChannelState.Writable);
        socket = new SocketAdaptor(this);
    }

    public enum ChannelState {
        Connectable,
        Readable,
        Writable
    }

    @Getter
    @Accessors(fluent = true)
    private final EnumSet<ChannelState> state = EnumSet.noneOf(ChannelState.class);

    public void addSelectionKey(TunnelingSelectionKey key) {
        keys.add(key);
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        channel.config().setOption(ChannelOption.valueOf(name.name()), value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return channel.config().getOption(ChannelOption.valueOf(name.name()));
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketChannel shutdownInput() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket socket() {
        return socket;
    }

    @Override
    public boolean isConnected() {
        return channel.isActive();
    }

    @Override
    public boolean isConnectionPending() {
        return channel.isOpen() && !channel.isActive();
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        brokerAddress = (InetSocketAddress) remote;
        ChannelFuture future = bootstrap.connect(tunnelServer);
        channel = future.channel();
        future.addListener(f -> keys.forEach(key -> {
            if ((key.interestOps() & SelectionKey.OP_CONNECT) != 0) {
                if (future.isSuccess()) {
                    state.add(ChannelState.Connectable);
                    key.selector().wakeup();
                } else {
                    errors.add(future.cause());
                }
            }
        }));
        return future.isSuccess();
    }

    @Override
    public boolean finishConnect() throws IOException {
        return channel.isActive();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return channel.remoteAddress();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ioLock.lock();
        try {
            ByteBuffer buffer = readBuffer.peekFirst();
            if (buffer == null) {
                return 0;
            }
            int read = Math.min(buffer.remaining(), dst.remaining());
            buffer.get(dst.array(), dst.position(), read);
            dst.position(dst.position() + read);
            if (!buffer.hasRemaining()) {
                readBuffer.pollFirst();
            }
            if (readBuffer.isEmpty()) {
                state.remove(ChannelState.Readable);
            }
            return read;
        } finally {
            ioLock.unlock();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        int totalRead = 0;
        for (int i = offset; i < offset + length; i++) {
            ByteBuffer dst = dsts[i];
            if (dst.hasRemaining()) {
                int read = read(dst);
                if (read > 0) {
                    totalRead += read;
                } else {
                    break;
                }
            } else {
                continue;
            }
        }
        return totalRead;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int writtenBytes = 0;
        if (writeBuffer == null) {
            int kafkaRequestSize = src.getInt();
            writtenBytes += 4;
            writeBuffer = ByteBuffer.allocate(4 + kafkaRequestSize);
            writeBuffer.putInt(kafkaRequestSize);
        }
        while (src.hasRemaining() && writeBuffer.hasRemaining()) {
            writeBuffer.put(src.get());
            writtenBytes++;
        }
        if (!writeBuffer.hasRemaining()) {
            writeBuffer.flip();
            int bytes = writeBuffer.remaining();
            ByteBuf buf = channel.alloc().buffer(bytes);
            buf.writeBytes(writeBuffer);
            FullHttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.POST,
                    "/proxy",
                    null,
                    DefaultHttpHeadersFactory
                            .headersFactory()
                            .newHeaders()
                            .add(HttpHeaderNames.HOST, String.format("%s:%d", brokerAddress.getHostName(), brokerAddress.getPort()))
                            .add(HttpHeaderNames.CONTENT_LENGTH, bytes)
                            .add(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream"),
                    DefaultHttpHeadersFactory
                            .trailersFactory()
                            .newEmptyHeaders());
            channel.write(request);
            writeBuffer = null;
        }
        return writtenBytes;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        int totalWritten = 0;
        for (int i = offset; i < offset + length; i++) {
            ByteBuffer src = srcs[i];
            if (src.hasRemaining()) {
                int written = write(src);
                if (written > 0) {
                    totalWritten += written;
                } else {
                    break;
                }
            } else {
                continue;
            }
        }
        return totalWritten;
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return channel.localAddress();
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
//        channel.close()
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        // noop
    }
}
