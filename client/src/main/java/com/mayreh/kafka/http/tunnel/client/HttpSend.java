package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpSend {
    private final ByteBuffer httpAndSize;
    private final TransportLayer transportLayer;
    private final InetSocketAddress brokerAddress;

    private int remainingMessageSize = -1;

    public HttpSend(TransportLayer transportLayer, InetSocketAddress brokerAddress) {
        this.transportLayer = transportLayer;
        this.brokerAddress = brokerAddress;
        httpAndSize = ByteBuffer.allocate(512);
    }

    /**
     * Write Kafka message to socket channel.
     * @return written bytes excluding HTTP line (i.e. only Kafka message is counted)
     */
    public int write(ByteBuffer message) throws IOException {
        int written = 0;
        if (remainingMessageSize < 0) {
            if (log.isDebugEnabled()) {
                ByteBuffer dup = message.duplicate();
                int size = dup.getInt();
                short apiKey = dup.getShort();
                short apiVersion = dup.getShort();
                int correlationId = dup.getInt();
                log.debug("Sending request {}. Size: {}, ApiKey: {}, ApiVersion: {}, CorrelationId: {}",
                          brokerAddress, size, apiKey, apiVersion, correlationId);
            }

            // we don't need to care about the fragmentation of int bytes
            // since kafka-clients always sends message size at initial call
            remainingMessageSize = message.getInt();
            written += 4;

            httpAndSize.put("POST /proxy HTTP/1.1\r\n".getBytes(StandardCharsets.UTF_8));
            httpAndSize.put(String.format("Host: %s:%d\r\n", brokerAddress.getHostName(), brokerAddress.getPort()).getBytes(StandardCharsets.UTF_8));
            httpAndSize.put("Content-Type: application/octet-stream\r\n".getBytes(StandardCharsets.UTF_8));
            httpAndSize.put("Content-Length: ".getBytes(StandardCharsets.UTF_8));

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
            transportLayer.write(httpAndSize);
        }
        if (!httpAndSize.hasRemaining()) {
            if (remainingMessageSize > 0) {
                // TODO: handle negative written bytes (EOF)
                int w = transportLayer.write(message);

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
