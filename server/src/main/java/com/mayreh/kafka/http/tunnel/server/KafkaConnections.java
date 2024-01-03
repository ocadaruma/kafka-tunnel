package com.mayreh.kafka.http.tunnel.server;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

public class KafkaConnections {
    private final EventLoopGroup workerGroup;
    private final Bootstrap bootstrap;
    private final ConcurrentMap<ConnectionId, KafkaConnection> connectionMap;

    @Value
    @Accessors(fluent = true)
    public static class ConnectionId {
        InetSocketAddress clientAddress;
        InetSocketAddress brokerAddress;
    }

    public KafkaConnections() {
        connectionMap = new ConcurrentHashMap<>();
        workerGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                 .channel(NioSocketChannel.class)
                 .option(ChannelOption.SO_KEEPALIVE, true)
                 .option(ChannelOption.TCP_NODELAY, true)
                 .handler(new ChannelInitializer<SocketChannel>() {
                     @Override
                     public void initChannel(SocketChannel ch) throws Exception {
                         ch.pipeline().addLast(
                                 new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4),
                                 new KafkaResponseHandler());
                     }
                 });
    }

    @Slf4j
    static class KafkaResponseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            byte[] response = new byte[buf.readableBytes()];
            try {
                buf.readBytes(response);
                ctx.channel().attr(KafkaConnection.ATTR_KEY).get().complete(response);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Exception caught", cause);
            ctx.close();
        }
    }

    public KafkaConnection getOrConnect(ConnectionId id) {
        return connectionMap.computeIfAbsent(id, key -> {
            ChannelFuture future = bootstrap.connect(id.brokerAddress);
            KafkaConnection conn = new KafkaConnection(future.channel());
            future.channel().attr(KafkaConnection.ATTR_KEY).set(conn);
            return conn;
        });
    }

    @RequiredArgsConstructor
    static class KafkaConnection {
        private static final AttributeKey<KafkaConnection> ATTR_KEY =
                AttributeKey.valueOf("KafkaConnection");

        private final Channel channel;
        private CompletableFuture<byte[]> responseFuture;

        /**
         * Send request to Kafka broker and wait for response
         */
        public CompletableFuture<byte[]> send(byte[] request) {
            synchronized (this) {
                if (responseFuture != null) {
                    throw new IllegalStateException("Request is already in progress");
                }
                responseFuture = new CompletableFuture<>();
            }

            ByteBuf buf = channel.alloc().buffer(request.length).writeBytes(request);
            CompletableFuture<Void> writeFuture = new CompletableFuture<>();
            channel.writeAndFlush(buf).addListener(f -> {
                if (f.isSuccess()) {
                    writeFuture.complete(null);
                } else {
                    writeFuture.completeExceptionally(f.cause());
                }
            });
            return writeFuture.thenCompose(f -> responseFuture);
        }

        /**
         * Complete in-flight request with response
         */
        public void complete(byte[] response) {
            CompletableFuture<byte[]> responseFuture;
            synchronized (this) {
                if (this.responseFuture == null) {
                    throw new IllegalStateException("Request is not in progress");
                }
                responseFuture = this.responseFuture;
                this.responseFuture = null;
            }
            responseFuture.complete(response);
        }
    }
}
