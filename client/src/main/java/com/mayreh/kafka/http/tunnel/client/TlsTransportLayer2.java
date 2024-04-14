//package com.mayreh.kafka.http.tunnel.client;
//
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.nio.channels.SelectionKey;
//import java.nio.channels.SocketChannel;
//
//import javax.net.ssl.SSLContext;
//
//import lombok.RequiredArgsConstructor;
//import tlschannel.ClientTlsChannel;
//import tlschannel.TlsChannel;
//import tlschannel.TlsChannelBuilder;
//import tlschannel.WouldBlockException;
//
//public class TlsTransportLayer2 implements TransportLayer {
//    private final TlsChannel channel;
//
//    public TlsTransportLayer2(SocketChannel delegate, SSLContext context) {
//        this.channel = ClientTlsChannel
//                .newBuilder(delegate, context)
//                .withRunTasks(true)
//                .build();
//    }
//
//    @Override
//    public void setSelectionKey(SelectionKey key) {
//        // no-op
//    }
//
//    @Override
//    public boolean hasBufferedRead() {
//        return false;
//    }
//
//    @Override
//    public boolean ready() {
//        return true;
//    }
//
//    @Override
//    public void handshake() throws IOException {
//        // no-op
//    }
//
//    @Override
//    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
//        try {
//            return channel.write(srcs, offset, length);
//        } catch (WouldBlockException e) {
//            return 0;
//        }
//    }
//
//    @Override
//    public long write(ByteBuffer[] srcs) throws IOException {
//        try {
//            return channel.write(srcs);
//        } catch (WouldBlockException e) {
//            return 0;
//        }
//    }
//
//    @Override
//    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
//        try {
//            return channel.read(dsts, offset, length);
//        } catch (WouldBlockException e) {
//            return 0;
//        }
//    }
//
//    @Override
//    public long read(ByteBuffer[] dsts) throws IOException {
//        try {
//            return channel.read(dsts);
//        } catch (WouldBlockException e) {
//            return 0;
//        }
//    }
//
//    @Override
//    public int read(ByteBuffer dst) throws IOException {
//        try {
//            return channel.write(dst);
//        } catch (WouldBlockException e) {
//            return 0;
//        }
//    }
//
//    @Override
//    public int write(ByteBuffer src) throws IOException {
//        try {
//            return channel.write(src);
//        } catch (WouldBlockException e) {
//            return 0;
//        }
//    }
//
//    @Override
//    public boolean isOpen() {
//        return channel.isOpen();
//    }
//
//    @Override
//    public void close() throws IOException {
//        channel.close();
//    }
//}
