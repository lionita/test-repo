package com.example.auction.app.adapters.out.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RealtimePushAdapter {
    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final AtomicLong idSequence = new AtomicLong();
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        long id = idSequence.incrementAndGet();
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(error -> emitters.remove(id));
        return emitter;
    }


    int activeConnections() {
        return emitters.size();
    }

    public void publish(String eventType, String payload) {
        for (Long id : new CopyOnWriteArrayList<>(emitters.keySet())) {
            SseEmitter emitter = emitters.get(id);
            if (emitter == null) {
                continue;
            }
            try {
                emitter.send(SseEmitter.event().name(eventType).data(payload));
            } catch (IOException ex) {
                emitter.completeWithError(ex);
                emitters.remove(id);
            }
        }
    }
}
