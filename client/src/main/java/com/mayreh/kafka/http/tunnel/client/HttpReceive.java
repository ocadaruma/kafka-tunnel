package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.experimental.Accessors;

public class HttpReceive {
    @Getter
    @Accessors(fluent = true)
    private final ByteBuffer lineBuffer;

    private final Map<String, String> headers = new HashMap<>();

    private final SocketChannel delegate;
    private final StringBuilder lineBuilder = new StringBuilder();

    private ParseState state = ParseState.StatusLine;
    private int remainingMessageSize = 0;

    enum ParseState {
        StatusLine,
        HeaderLine,
        CarriageReturn,
        LineStart,
        CarriageReturnAtHttpEnd,
        KafkaMessage,
    }

    public HttpReceive(SocketChannel delegate) {
        lineBuffer = ByteBuffer.allocate(1024);
        this.delegate = delegate;
    }

    public int read(ByteBuffer dst) throws IOException {
        if (state == ParseState.KafkaMessage) {
            int read = delegate.read(dst);
            remainingMessageSize -= read;
            return read;
        }

        lineBuffer.clear();

        // Prevent reading more than remaining bytes.
        // This is to simplify the buffer management.
        // Let's say the delegate has below content:
        //   HTTP/1.1 200 OK\r\n
        //   Content-Length: 100\r\n
        //   \r\n
        //   <100 bytes of Kafka message>
        // And say the dst has 42 bytes remaining.
        // If we don't limit the lineBuffer, all content will be read into the lineBuffer.
        // However, since the dst has only 42 bytes remaining, the caller will expect
        // up to 42 bytes of Kafka message is read, and the remaining 58 bytes will be read
        // in the next call.
        // Since we can't "rewind" the delegate channel, to do so, we need to keep
        // remaining 58 bytes somewhere, which may complicate the implementation.
        lineBuffer.limit(dst.remaining());

        delegate.read(lineBuffer);
        lineBuffer.flip();

        loop: while (lineBuffer.hasRemaining()) {
            byte b = lineBuffer.get();
            switch (state) {
                case StatusLine:
                    if (b == '\r') {
                        checkHttpStatus(lineBuilder.toString());
                        lineBuilder.setLength(0);
                        state = ParseState.CarriageReturn;
                    } else {
                        lineBuilder.append((char) b);
                    }
                    break;
                case HeaderLine:
                    if (b == '\r') {
                        parseHeader(lineBuilder.toString());
                        lineBuilder.setLength(0);
                        state = ParseState.CarriageReturn;
                    } else {
                        lineBuilder.append((char) b);
                    }
                    break;
                case CarriageReturn:
                    if (b == '\n') {
                        state = ParseState.LineStart;
                    } else {
                        throw new IOException("Invalid HTTP response");
                    }
                    break;
                case LineStart:
                    if (b == '\r') {
                        state = ParseState.CarriageReturnAtHttpEnd;
                    } else {
                        state = ParseState.HeaderLine;
                        lineBuilder.append((char) b);
                    }
                    break;
                case CarriageReturnAtHttpEnd:
                    if (b == '\n') {
                        state = ParseState.KafkaMessage;
                        remainingMessageSize = Integer.parseInt(headers.get("Content-Length"));
                        break loop;
                    } else {
                        throw new IOException("Invalid HTTP response");
                    }
                default:
                    throw new IllegalStateException("Invalid state: " + state);
            }
        }

        int read = 0;
        if (state == ParseState.KafkaMessage) {
            read += Math.min(dst.remaining(), lineBuffer.remaining());
            remainingMessageSize -= read;
            dst.put(lineBuffer);
        }

        return read;
    }

    public boolean hasRemaining() {
        return state != ParseState.KafkaMessage || remainingMessageSize > 0;
    }

    private static void checkHttpStatus(String statusLine) {
        String[] parts = statusLine.split(" ");
        if (parts.length != 3) {
            throw new IllegalStateException("Invalid status line: " + statusLine);
        }
        if (!parts[0].equals("HTTP/1.1")) {
            throw new IllegalStateException("Invalid status line: " + statusLine);
        }
        int statusCode = Integer.parseInt(parts[1]);
        if (statusCode != 200) {
            throw new IllegalStateException("Invalid status line: " + statusLine);
        }
    }

    private void parseHeader(String headerLine) {
        String[] parts = headerLine.split(":");
        if (parts.length != 2) {
            throw new IllegalStateException("Invalid header line: " + headerLine);
        }

        String key = parts[0].trim();
        String value = parts[1].trim();

        headers.put(key, value);
    }
}
