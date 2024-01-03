package com.mayreh.kafka.http.tunnel.server;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.Server;

public class TunnelingServer implements AutoCloseable {
    private final Server server;

    public TunnelingServer(int port) {
        server = Server
                .builder()
                .service("/proxy", (ctx, req) -> {
                    ctx.remoteAddress()
                    req.aggregate().join()
                    return HttpResponse.of(HttpStatus.OK,
                                           MediaType.OCTET_STREAM,
                                           )
                })
                .http(port)
                .build();
        server.start().join();
    }

    public static void main(String[] args) {
        TunnelingServer server = new TunnelingServer(Integer.parseInt(args[0]));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }

    @Override
    public void close() {
        server.stop().join();
        server.close();
    }
}
