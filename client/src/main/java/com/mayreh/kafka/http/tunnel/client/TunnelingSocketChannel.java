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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TunnelingSocketChannel extends SocketChannel {
    private InetSocketAddress brokerAddress;
    private ByteBuffer writeBuffer;
    @Getter
    @Accessors(fluent = true)
    private volatile int readyOps;
    private final SocketChannel javaChannel;
    private final Channel channel;
    private final InetSocketAddress tunnelServer;
    private final Set<TunnelingSelectionKey> keys = ConcurrentHashMap.newKeySet();
    private final Deque<ByteBuffer> readBuffer = new ArrayDeque<>();
    // TODO: Handle errors
    private final Deque<Throwable> errors = new ArrayDeque<>();
    private final ReentrantLock ioLock = new ReentrantLock();
    private final CompletableFuture<Void> registrationFuture = new CompletableFuture<>();

    public TunnelingSocketChannel(
            InetSocketAddress tunnelServer,
            SelectorProvider provider,
            SelectorProvider defaultProvider) throws IOException {
        super(provider);
        this.tunnelServer = tunnelServer;
        // Since actual write operation to the socket is done by Netty and we just
        // store written-bytes in memory in this class, we always consider the channel writable
        readyOps |= SelectionKey.OP_WRITE;
        javaChannel = defaultProvider.openSocketChannel();
        channel = new NioSocketChannel(javaChannel);
    }

    public void register(SslContext sslContext, EventLoopGroup eventLoopGroup) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap
                .group(eventLoopGroup)
                .channelFactory(() -> channel)
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
                                        if (msg.status().code() != 200) {
                                            ioLock.lock();
                                            try {
                                                errors.addLast(
                                                        new IOException("Invalid status: " + msg.status()));
                                                return;
                                            } finally {
                                                ioLock.unlock();
                                            }
                                        }
                                        ByteBuffer buffer = ByteBuffer.allocate(msg.content().readableBytes());
                                        msg.content().readBytes(buffer);
                                        ioLock.lock();
                                        try {
                                            buffer.flip();
                                            readBuffer.add(buffer);
                                            readyOps |= SelectionKey.OP_READ;
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
        bootstrap.register()
                 // TODO: Handle registration failure
                 .addListener(f -> registrationFuture.complete(null))
                 .syncUninterruptibly();
    }

    public void addSelectionKey(TunnelingSelectionKey key) {
        keys.add(key);
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        return javaChannel.bind(local);
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        return javaChannel.setOption(name, value);
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return javaChannel.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return javaChannel.supportedOptions();
    }

    @Override
    public SocketChannel shutdownInput() throws IOException {
        return javaChannel.shutdownInput();
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        return javaChannel.shutdownOutput();
    }

    @Override
    public Socket socket() {
        return javaChannel.socket();
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
        registrationFuture.whenComplete((v, t) -> {
            ChannelFuture future = channel.connect(tunnelServer);
            future.addListener(f -> keys.forEach(key -> {
                if ((key.interestOps() & SelectionKey.OP_CONNECT) != 0) {
                    if (future.isSuccess()) {
                        readyOps |= SelectionKey.OP_CONNECT;
                        key.selector().wakeup();
                    } else {
                        errors.addLast(future.cause());
                    }
                }
            }));
        });

        return false;
    }

    @Override
    public boolean finishConnect() throws IOException {
        return channel.isActive();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return javaChannel.getRemoteAddress();
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
                readyOps &= ~SelectionKey.OP_READ;
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
                    buf,
                    DefaultHttpHeadersFactory
                            .headersFactory()
                            .newHeaders()
                            .add(HttpHeaderNames.HOST, String.format("%s:%d", brokerAddress.getHostName(), brokerAddress.getPort()))
                            .add(HttpHeaderNames.CONTENT_LENGTH, bytes)
                            .add(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream"),
                    DefaultHttpHeadersFactory
                            .trailersFactory()
                            .newEmptyHeaders());
            channel.writeAndFlush(request);
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
        return javaChannel.getLocalAddress();
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        channel.close().syncUninterruptibly();
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        // noop
    }
}
