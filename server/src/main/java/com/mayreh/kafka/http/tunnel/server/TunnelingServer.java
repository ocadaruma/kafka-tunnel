package com.mayreh.kafka.http.tunnel.server;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import com.mayreh.kafka.http.tunnel.server.KafkaConnections.ConnectionId;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.logging.LoggingService;

import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TunnelingServer implements AutoCloseable {
    private final Server server;
    private final KafkaConnections connections = new KafkaConnections();
    @Getter
    @Accessors(fluent = true)
    private final int port;

    public TunnelingServer(int port) {
        HttpService proxyService = (ctx, req) -> {
            String host = req.headers().get("Host");
            String brokerHost = host.substring(0, host.indexOf(':'));
            int brokerPort = Integer.parseInt(host.substring(host.indexOf(':') + 1));
            InetSocketAddress brokerAddress = new InetSocketAddress(brokerHost, brokerPort);
            ConnectionId id = new ConnectionId(ctx.remoteAddress(), brokerAddress);
            return HttpResponse.of(req.aggregate().thenCompose(agg -> {
                if (log.isDebugEnabled()) {
                    ByteBuffer buf = ByteBuffer.allocate(4 + 2 + 2 + 4);
                    buf.put(agg.content().array(), 0, buf.capacity());
                    buf.flip();
                    log.debug("Received request. Size: {}, ApiKey: {}, ApiVersion: {}, CorrelationId: {}",
                              buf.getInt(),
                              buf.getShort(),
                              buf.getShort(),
                              buf.getInt());
                }

                return connections
                        .getOrConnect(id)
                        .send(agg.content().array())
                        .thenApply(res -> {
                            return HttpResponse.of(HttpStatus.OK,
                                                   MediaType.OCTET_STREAM,
                                                   res);
                        });
            }));
        };

        server = Server
                .builder()
                .service("/proxy", proxyService.decorate(LoggingService.newDecorator()))
                .http(port)
                .build();
        server.start().join();
        this.port = server.activeLocalPort();
    }

    public static void main(String[] args) {
        int port = 0;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        TunnelingServer server = new TunnelingServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }

    @Override
    public void close() {
        server.stop().join();
        server.close();
    }
}
