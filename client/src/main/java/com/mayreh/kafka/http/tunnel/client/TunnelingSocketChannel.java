//package com.mayreh.kafka.http.tunnel.client;
//
//import java.io.IOException;
//import java.net.InetSocketAddress;
//import java.net.Socket;
//import java.net.SocketAddress;
//import java.net.SocketOption;
//import java.nio.ByteBuffer;
//import java.nio.channels.SelectionKey;
//import java.nio.channels.SocketChannel;
//import java.nio.channels.spi.SelectorProvider;
//import java.security.KeyManagementException;
//import java.security.NoSuchAlgorithmException;
//import java.security.SecureRandom;
//import java.security.cert.CertificateException;
//import java.security.cert.X509Certificate;
//import java.util.Arrays;
//import java.util.Set;
//
//import javax.net.ssl.SSLContext;
//import javax.net.ssl.SSLEngine;
//import javax.net.ssl.TrustManager;
//import javax.net.ssl.X509TrustManager;
//
//import lombok.Getter;
//import lombok.experimental.Accessors;
//import lombok.extern.slf4j.Slf4j;
//
//@Slf4j
//public class TunnelingSocketChannel extends SocketChannel {
//    @Getter
//    @Accessors(fluent = true)
//    private final SocketChannel delegate;
//    @Getter
//    @Accessors(fluent = true)
//    private final TransportLayer transportLayer;
//    private final InetSocketAddress tunnelServer;
//    private InetSocketAddress brokerAddress = null;
//    private HttpSend send = null;
//    private HttpReceive receive = null;
//
//    public TunnelingSocketChannel(
//            SelectorProvider provider,
//            SocketChannel delegate,
//            InetSocketAddress tunnelServer,
//            boolean enableTls) {
//        super(provider);
//        if (enableTls) {
//            try {
//                TrustManager[] trustAllCerts = new TrustManager[] {
//                        new X509TrustManager() {
//                            @Override
//                            public void checkClientTrusted(X509Certificate[] chain, String authType)
//                                    throws CertificateException {
//                            }
//
//                            @Override
//                            public void checkServerTrusted(X509Certificate[] chain, String authType)
//                                    throws CertificateException {
//                            }
//
//                            @Override
//                            public X509Certificate[] getAcceptedIssuers() {
//                                return new X509Certificate[0];
//                            }
//                        }
//                };
//                SSLContext sslContext = SSLContext.getInstance("SSL");
//                sslContext.init(null, trustAllCerts, new SecureRandom());
//                SSLEngine engine = sslContext.createSSLEngine();
//                engine.setUseClientMode(true);
//                // TODO: implement TLSv1.3 post handshake
//                engine.setEnabledProtocols(new String[] { "TLSv1.2" });
//
//                transportLayer = new TlsTransportLayer(delegate, engine);
////                transportLayer = new TlsTransportLayer2(delegate, sslContext);
//            } catch (NoSuchAlgorithmException | KeyManagementException e) {
//                throw new RuntimeException(e);
//            }
//        } else {
//            transportLayer = new CleartextTransportLayer(delegate);
//        }
//        this.delegate = delegate;
//        this.tunnelServer = tunnelServer;
//    }
//
//    public void setSelectionKey(SelectionKey key) {
//        transportLayer.setSelectionKey(key);
//    }
//
//    @Override
//    public SocketChannel bind(SocketAddress local) throws IOException {
//        delegate.bind(local);
//        return this;
//    }
//
//    @Override
//    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
//        delegate.setOption(name, value);
//        return this;
//    }
//
//    @Override
//    public <T> T getOption(SocketOption<T> name) throws IOException {
//        return delegate.getOption(name);
//    }
//
//    @Override
//    public Set<SocketOption<?>> supportedOptions() {
//        return delegate.supportedOptions();
//    }
//
//    @Override
//    public SocketChannel shutdownInput() throws IOException {
//        return delegate.shutdownInput();
//    }
//
//    @Override
//    public SocketChannel shutdownOutput() throws IOException {
//        return delegate.shutdownOutput();
//    }
//
//    @Override
//    public Socket socket() {
//        return delegate.socket();
//    }
//
//    @Override
//    public boolean isConnected() {
//        return delegate.isConnected();
//    }
//
//    @Override
//    public boolean isConnectionPending() {
//        return delegate.isConnectionPending();
//    }
//
//    @Override
//    public boolean connect(SocketAddress remote) throws IOException {
//        brokerAddress = (InetSocketAddress) remote;
//        return delegate.connect(tunnelServer);
//    }
//
//    @Override
//    public boolean finishConnect() throws IOException {
//        return delegate.finishConnect();
//    }
//
//    @Override
//    public SocketAddress getRemoteAddress() throws IOException {
//        return delegate.getRemoteAddress();
//    }
//
//    @Override
//    public int read(ByteBuffer dst) throws IOException {
//        if (!transportLayer.ready()) {
//            transportLayer.handshake();
//            return 0;
//        }
//        if (receive == null) {
//            receive = new HttpReceive(transportLayer);
//            log.debug("Started receiving response: {}", brokerAddress);
//
////            if (Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(s ->
////                                                                                       s.getMethodName().equals("awaitNodeReady"))) {
////                log.debug("await node ready on channel");
////            }
//        }
//        int read = receive.read(dst);
//        if (!receive.hasRemaining()) {
//            log.debug("Received response: {}", brokerAddress);
//            receive = null;
//        }
//        return read;
//    }
//
//    @Override
//    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
//        throw new UnsupportedOperationException();
//    }
//
//    @Override
//    public int write(ByteBuffer src) throws IOException {
//        if (!transportLayer.ready()) {
//            transportLayer.handshake();
//            return 0;
//        }
//        if (send == null) {
//            send = new HttpSend(transportLayer, brokerAddress);
//        }
//        int written = send.write(src);
//        if (!send.hasRemaining()) {
//            send = null;
//        }
//        return written;
//    }
//
//    @Override
//    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
//        int totalWritten = 0;
//        for (int i = offset; i < offset + length; i++) {
//            if (srcs[i].hasRemaining()) {
//                totalWritten += write(srcs[i]);
//            }
//            if (srcs[i].hasRemaining()) {
//                break;
//            }
//        }
//        return totalWritten;
//    }
//
//    @Override
//    public SocketAddress getLocalAddress() throws IOException {
//        return delegate.getLocalAddress();
//    }
//
//    @Override
//    protected void implCloseSelectableChannel() throws IOException {
//        if (transportLayer != null) {
//            transportLayer.close();
//        }
//        delegate.close();
////        ReflectionUtil.call(delegate, "implCloseSelectableChannel");
//    }
//
//    @Override
//    protected void implConfigureBlocking(boolean block) throws IOException {
//        delegate.configureBlocking(block);
////        ReflectionUtil.call(delegate,
////                            "implConfigureBlocking",
////                            new Class<?>[] { boolean.class },
////                            block);
//    }
//
////    @Override
////    public FileDescriptor getFD() {
////        return ((SelChImpl) delegate).getFD();
////    }
////
////    @Override
////    public int getFDVal() {
////        return ((SelChImpl) delegate).getFDVal();
////    }
////
////    @Override
////    public boolean translateAndUpdateReadyOps(int ops, SelectionKeyImpl ski) {
////        return ((SelChImpl) delegate).translateAndUpdateReadyOps(ops, ski);
////    }
////
////    @Override
////    public boolean translateAndSetReadyOps(int ops, SelectionKeyImpl ski) {
////        return ((SelChImpl) delegate).translateAndSetReadyOps(ops, ski);
////    }
////
////    @Override
////    public int translateInterestOps(int ops) {
////        return ((SelChImpl) delegate).translateInterestOps(ops);
////    }
////
////    @Override
////    public void kill() throws IOException {
////        ((SelChImpl) delegate).kill();
////    }
//}
