package com.example.auction.app.adapters.out.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimePushAdapterTest {

    @Test
    void connectAndComplete_tracksActiveConnections() {
        RealtimePushAdapter adapter = new RealtimePushAdapter();

        SseEmitter emitter = adapter.connect();

        assertThat(adapter.activeConnections()).isEqualTo(1);

        emitter.complete();

        assertThat(adapter.activeConnections()).isEqualTo(0);
    }

    @Test
    void publish_toConnectedEmitter_keepsConnectionAlive() {
        RealtimePushAdapter adapter = new RealtimePushAdapter();
        adapter.connect();

        adapter.publish("bid.placed", "{\"auctionId\":\"a1\"}");

        assertThat(adapter.activeConnections()).isEqualTo(1);
    }
}
