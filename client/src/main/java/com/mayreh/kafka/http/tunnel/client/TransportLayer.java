package com.mayreh.kafka.http.tunnel.client;

import java.io.IOException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectionKey;

public interface TransportLayer extends ScatteringByteChannel, GatheringByteChannel {
    void setSelectionKey(SelectionKey key);
    boolean hasBufferedRead();
    boolean ready();
    void handshake() throws IOException;
}
