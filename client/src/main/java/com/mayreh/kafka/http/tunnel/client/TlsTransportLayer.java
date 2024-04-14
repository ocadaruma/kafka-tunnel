package com.mayreh.kafka.http.tunnel.client;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLKeyException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TlsTransportLayer implements TransportLayer {
    private static final String TLS13 = "TLSv1.3";
    private static final ByteBuffer emptyBuffer = ByteBuffer.allocate(0);

    private enum State {
        Initial,
        Handshaking,
        HandshakeFailed,
        PostHandshake,
        Ready,
        Closing,
    }

    private final SocketChannel delegate;
    private final SSLEngine sslEngine;

    @Setter
    private SelectionKey selectionKey;
    private State state = State.Initial;
    private ByteBuffer netReadBuffer;
    private ByteBuffer netWriteBuffer;
    private ByteBuffer appReadBuffer;
    // This is to hold currently written buffer to return written bytes only when the buffer is
    // fully flushed to network to prevent OP_WRITE is removed which causes netWriteBuffer never be flushed
    private ByteBuffer appWriteBuffer;

    @Override
    public boolean hasBufferedRead() {
        return netReadBuffer.hasRemaining();
    }

    @Override
    public boolean ready() {
        return state == State.Ready ||
               state == State.PostHandshake;
    }

    @Override
    public void handshake() throws IOException {
        while (true) {
            switch (state) {
                case Initial: {
                    netReadBuffer = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
                    netWriteBuffer = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
                    netWriteBuffer.limit(0);

                    state = State.Handshaking;
                    sslEngine.beginHandshake(); // TODO: handle exception?
                    break;
                }
                case HandshakeFailed:
                case Handshaking: {
                    //////////////////////////////////////////////////
                    // ==== BEGIN Network I/O section ==== //

                    // Read any available bytes before attempting any writes to ensure that handshake failures
                    // reported by the peer are processed regardless of subsequent writes succeeding or not
                    // (which will likely fail when peer closes connection on handshake failure)
//                    int netRead = 0;
//                    if (selectionKey.isReadable()) {
//                        netRead = delegate.read(netReadBuffer);
//                        if (netRead < 0) {
//                            // TODO: handle EOF
//                        }
//                        if (netRead > 0) {
//                            netReadBuffer.flip();
//                        }
//                    }
                    if (!tryFlushFully(netWriteBuffer)) {
                        return;
                    }
                    netWriteBuffer.clear().limit(0);
                    // ==== END Network I/O section ==== //
                    //////////////////////////////////////////////////

                    // TODO: maybeThrowSslAuthenticationException

                    HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
                    switch (handshakeStatus) {
                        case NEED_TASK:
                            runDelegatedTasks();
                            break;
                        case NEED_WRAP: {
                            netWriteBuffer.clear();
                            SSLEngineResult result = sslEngine.wrap(emptyBuffer, netWriteBuffer);
                            netWriteBuffer.flip();
                            switch (result.getStatus()) {
                                case BUFFER_OVERFLOW:
                                    netWriteBuffer = growIfNecessary(
                                            netWriteBuffer.compact(),
                                            sslEngine.getSession().getPacketBufferSize());
                                    break;
                                case CLOSED:
                                    // TODO: handle closed
                                    throw new EOFException();
                                default:
                                    break;
                            }
                            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                            break;
                        }
                        case NEED_UNWRAP:
//                            if (netRead <= 0) {
//                                return;
//                            }
                            int nRead = delegate.read(netReadBuffer);
                            if (nRead > 0) {
                                netReadBuffer.flip();
                            } else if (netReadBuffer.position() <= 0) {
                                return;
                            }
                            SSLEngineResult result = sslEngine.unwrap(netReadBuffer, emptyBuffer);
                            switch (result.getStatus()) {
                                case BUFFER_UNDERFLOW:
                                    netReadBuffer = growIfNecessary(
                                            netReadBuffer.compact(),
                                            sslEngine.getSession().getPacketBufferSize());
                                    break;
                                case BUFFER_OVERFLOW:
                                    throw new IllegalStateException("Not expected");
                                case CLOSED:
                                    // TODO: handle closed
                                    throw new EOFException();
                                default:
                                    break;
                            }
                            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                            break;
                        case NOT_HANDSHAKING:
                        case FINISHED: {
                            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                            state = TLS13.equals(sslEngine.getSession().getProtocol()) ? State.PostHandshake : State.Ready;
                            appReadBuffer = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
                            return;
                        }
                        default:
                            throw new IllegalStateException("Unexpected handshake status: " + handshakeStatus);
                    }
                    break;
                }
                case PostHandshake:
                case Ready:
                    throw new SSLHandshakeException("Renegotiation is not supported");
                case Closing:
                    throw new IllegalStateException("Channel is in closing state");
            }
        }
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
    public long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (state == State.Closing) {
            return -1;
        }
        if (!ready()) {
            return 0;
        }

        // We have to return written bytes only when the buffer is fully flushed
        // otherwise OP_WRITE might be removed by KafkaChannel and netWriteBuffer might never be flushed
        if (appWriteBuffer == null) {
            appWriteBuffer = src.duplicate();
            // mark here so that we can get total written bytes by reset() after the buffer is fully flushed
            appWriteBuffer.mark();
        } else if (!(appWriteBuffer.hasRemaining() || netWriteBuffer.hasRemaining())) {
            // current buffer is fully flushed to network so now ready to return written bytes
            int written = appWriteBuffer.reset().remaining();
            appWriteBuffer = null;
            return written;
        }

        while (tryFlushFully(netWriteBuffer) && appWriteBuffer.hasRemaining()) {
            netWriteBuffer.clear();
            SSLEngineResult result = sslEngine.wrap(appWriteBuffer, netWriteBuffer);

            switch (result.getStatus()) {
                case BUFFER_OVERFLOW:
                    netWriteBuffer = growIfNecessary(
                            netWriteBuffer.compact(),
                            sslEngine.getSession().getPacketBufferSize());
                    break;
                case CLOSED:
                    // TODO: handle eof
                    break;
                default:
                    break;
            }
        }

        return 0;
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
    public long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (state == State.Closing) {
            return -1;
        }
        if (!ready()) {
            return 0;
        }
        int read = 0;

        int netRead;
        while ((netRead = delegate.read(netReadBuffer)) > 0 || dst.hasRemaining()) {
            netReadBuffer.flip();
            SSLEngineResult result = sslEngine.unwrap(netReadBuffer, appReadBuffer);
            switch (result.getStatus()) {
                case OK:
                    read += readFromAppBuffer(dst);
                    break;
                case BUFFER_OVERFLOW:
                    appReadBuffer = growIfNecessary(
                            appReadBuffer.compact(),
                            sslEngine.getSession().getApplicationBufferSize());
                    break;
                case BUFFER_UNDERFLOW:
                    netReadBuffer = growIfNecessary(
                            netReadBuffer.compact(),
                            sslEngine.getSession().getPacketBufferSize());
                    break;
                case CLOSED:
                    // TODO: handle EOF
                    break;
                default:
                    break;
            }

        }
        if (netRead < 0) {
            // TODO: handle EOF
        }

        return read;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        State prevState = state;
        state = State.Closing;
        sslEngine.closeOutbound();
        try {
            // If SSL handshake isn't initiated yet, we don't need to close in orderly manner
            if (prevState != State.Initial && delegate.isConnected()) {
                if (!tryFlushFully(netWriteBuffer)) {
                    throw new IOException("Remaining data in the network buffer, can't send SSL close message.");
                }
                netWriteBuffer.clear();
                SSLEngineResult result = sslEngine.wrap(emptyBuffer, netWriteBuffer);
                if (result.getStatus() != Status.CLOSED) {
                    throw new IOException("SSLEngine didn't close. Final status: " + result.getStatus());
                }
                netWriteBuffer.flip();
                tryFlushFully(netWriteBuffer);
            }
        } catch (IOException e) {
            log.debug("Failed to send SSL Close message", e);
        } finally {
            delegate.close();
            netReadBuffer = null;
            netWriteBuffer = null;
            appReadBuffer = null;
            appWriteBuffer = null;
        }
    }

    private boolean tryFlushFully(ByteBuffer buffer) throws IOException {
        int remaining = buffer.remaining();
        if (remaining > 0) {
            int written = delegate.write(buffer);
            return written >= remaining;
        }
        return true;
    }

    private int readFromAppBuffer(ByteBuffer dst) {
        appReadBuffer.flip();
        int remaining = Math.min(appReadBuffer.remaining(), dst.remaining());
        if (remaining > 0) {
            int limit = appReadBuffer.limit();
            appReadBuffer.limit(appReadBuffer.position() + remaining);
            dst.put(appReadBuffer);
            appReadBuffer.limit(limit);
        }
        appReadBuffer.compact();
        return remaining;
    }

    private void runDelegatedTasks() {
        for (;;) {
            Runnable task = sslEngine.getDelegatedTask();
            if (task == null) {
                break;
            }
            task.run();
        }
    }

    private static boolean isHandshakeError(SSLException e) {
        return e instanceof SSLHandshakeException ||
               e instanceof SSLProtocolException ||
               e instanceof SSLPeerUnverifiedException ||
               e instanceof SSLKeyException ||
               e.getMessage().contains("Unrecognized SSL message") ||
               e.getMessage().contains("Received fatal alert: ");
    }

    private static ByteBuffer growIfNecessary(ByteBuffer buffer, int minCapacity) {
        if (buffer.capacity() < minCapacity) {
            ByteBuffer newBuffer = ByteBuffer.allocate(minCapacity);
            buffer.flip();
            newBuffer.put(buffer);
            return newBuffer;
        }
        return buffer;
    }
}
