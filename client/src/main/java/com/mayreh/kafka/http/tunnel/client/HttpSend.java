package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class HttpSend {
    private static final byte[] httpPrefix =
            ("POST /proxy HTTP/1.1\r\n" +
             "Host: localhost:8080\r\n" +
             "Content-Type: application/octet-stream\r\n" +
             "Content-Length: ").getBytes(StandardCharsets.UTF_8);

    private final ByteBuffer httpAndSize;
    private final SocketChannel delegate;

    private int remainingMessageSize = -1;

    public HttpSend(SocketChannel delegate) {
        this.delegate = delegate;
        // allocate max possible size of HTTP line + size
        // prefix + 10 (length of "2147483647") + 4 (\r\n\r\n) + 4 (size of kafka message part)
        httpAndSize = ByteBuffer.allocate(httpPrefix.length + 10 + 4 + 4);
        httpAndSize.put(httpPrefix);
    }

    /**
     * Write Kafka message to socket channel.
     * @return written bytes excluding HTTP line (i.e. only Kafka message is counted)
     */
    public int write(ByteBuffer message) throws IOException {
        int written = 0;
        if (remainingMessageSize < 0) {
            // we don't need to care about the fragmentation of int bytes
            // since kafka-clients always sends message size in initial call
            remainingMessageSize = message.getInt();
            written += 4;

            // content length will be the size of Kafka message + 4 (size of kafka message which we just read)
            httpAndSize.put(String.valueOf(remainingMessageSize + 4).getBytes(StandardCharsets.UTF_8));
            httpAndSize.put((byte) '\r');
            httpAndSize.put((byte) '\n');
            httpAndSize.put((byte) '\r');
            httpAndSize.put((byte) '\n');
            httpAndSize.putInt(remainingMessageSize);
            httpAndSize.flip();
        }
        if (httpAndSize.hasRemaining()) {
            delegate.write(httpAndSize);
        }
        if (!httpAndSize.hasRemaining()) {
            if (remainingMessageSize > 0) {
                int w = delegate.write(message);
                remainingMessageSize -= w;
                written += w;
            }
        }
        return written;
    }

    public boolean hasRemaining() {
        return httpAndSize.hasRemaining() || remainingMessageSize > 0;
    }
}
