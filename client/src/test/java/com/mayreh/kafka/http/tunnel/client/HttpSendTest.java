package com.mayreh.kafka.http.tunnel.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpSendTest {
    @Mock
    private TransportLayer transportLayer;

    @Test
    public void testWrite() throws Exception {
        ByteBuffer written = ByteBuffer.allocate(1024);
        doAnswer(inv -> {
            ByteBuffer dst = inv.getArgument(0, ByteBuffer.class);
            int len = dst.remaining();
            written.put(dst);
            // assume all bytes are written
            return len;
        }).when(transportLayer).write(any(ByteBuffer.class));

        HttpSend send = new HttpSend(transportLayer, new InetSocketAddress("localhost", 9092));

        assertTrue(send.hasRemaining());

        ByteBuffer message = ByteBuffer.allocate(4 + 3);
        message.putInt(3);
        message.put("foo".getBytes(StandardCharsets.UTF_8));
        message.flip();

        assertEquals(7, send.write(message));
        written.flip();

        byte[] httpLine = ("POST /proxy HTTP/1.1\r\n" +
                           "Host: localhost:9092\r\n" +
                           "Content-Type: application/octet-stream\r\n" +
                           "Content-Length: 7\r\n" +
                           "\r\n").getBytes(StandardCharsets.UTF_8);
        ByteBuffer expected = ByteBuffer.allocate(httpLine.length + 7);
        expected.put(httpLine);
        expected.putInt(3);
        expected.put("foo".getBytes(StandardCharsets.UTF_8));
        expected.flip();

        byte[] actual = new byte[written.limit()];
        written.get(actual);
        assertArrayEquals(expected.array(), actual);
        assertFalse(send.hasRemaining());
    }
}
