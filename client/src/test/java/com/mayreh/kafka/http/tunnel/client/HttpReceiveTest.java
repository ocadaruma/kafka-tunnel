package com.mayreh.kafka.http.tunnel.client;

import static java.util.Arrays.copyOfRange;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpReceiveTest {
    @Mock
    private SocketChannel delegate;

    @Test
    public void testRead() throws Exception {
        byte[] content = getBytes(
                "HTTP/1.1 200 OK\r\n" +
                // byte representation of "foo".length (4) + "foo".length
                "Content-Length: 7\r\n" +
                "\r\n",
                "foo".getBytes(StandardCharsets.UTF_8));
        int[] pos = {0};
        doAnswer(inv -> {
            ByteBuffer dst = inv.getArgument(0, ByteBuffer.class);
            int read = Math.min(dst.remaining(), content.length - pos[0]);
            dst.put(content, pos[0], read);
            pos[0] += read;
            return read;
        }).when(delegate).read(any(ByteBuffer.class));

        HttpReceive receive = new HttpReceive(delegate);

        assertTrue(receive.hasRemaining());

        ByteBuffer dst = ByteBuffer.allocate(1024);
        assertEquals(7, receive.read(dst));

        dst.flip();
        assertEquals(3, dst.getInt());
        assertEquals("foo", new String(dst.array(), 4, 3, StandardCharsets.UTF_8));
        assertFalse(receive.hasRemaining());
    }

    @Test
    public void testReadNeedMultipleCall() throws Exception {
        byte[] content = getBytes(
                "HTTP/1.1 200 OK\r\n" +
                // byte representation of "foo".length (4) + "foo".length
                "Content-Length: 7\r\n" +
                "\r\n",
                "foo".getBytes(StandardCharsets.UTF_8));
        int[] pos = {0};
        doAnswer(inv -> {
            ByteBuffer dst = inv.getArgument(0, ByteBuffer.class);
            int read = Math.min(dst.remaining(), content.length - pos[0]);
            dst.put(content, pos[0], read);
            pos[0] += read;
            return read;
        }).when(delegate).read(any(ByteBuffer.class));

        HttpReceive receive = new HttpReceive(delegate);

        assertTrue(receive.hasRemaining());

        ByteBuffer dst = ByteBuffer.allocate(10);
        assertEquals(0, receive.read(dst)); assertTrue(receive.hasRemaining()); dst.clear();
        assertEquals(0, receive.read(dst)); assertTrue(receive.hasRemaining()); dst.clear();
        assertEquals(0, receive.read(dst)); assertTrue(receive.hasRemaining()); dst.clear();

        ByteBuffer size = ByteBuffer.allocate(4);
        size.putInt(3);

        assertEquals(2, receive.read(dst)); assertTrue(receive.hasRemaining());
        assertArrayEquals(copyOfRange(size.array(), 0, 2), copyOfRange(dst.array(), 0, 2)); dst.clear();

        assertEquals(5, receive.read(dst)); assertFalse(receive.hasRemaining());

        assertArrayEquals(copyOfRange(size.array(), 2, 4), copyOfRange(dst.array(), 0, 2));
        assertEquals("foo", new String(dst.array(), 2, 3, StandardCharsets.UTF_8));
    }

    private static byte[] getBytes(String httpLine, byte[] kafkaPart) {
        byte[] httpLineBytes = httpLine.getBytes(StandardCharsets.UTF_8);
        ByteBuffer result = ByteBuffer.allocate(httpLineBytes.length + 4 + kafkaPart.length);
        result.put(httpLineBytes);
        result.putInt(kafkaPart.length);
        result.put(kafkaPart);
        return result.array();
    }
}
