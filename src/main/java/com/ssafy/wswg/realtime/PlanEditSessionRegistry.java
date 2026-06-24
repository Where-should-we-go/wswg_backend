package com.ssafy.wswg.realtime;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class PlanEditSessionRegistry {
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByTripId = new ConcurrentHashMap<>();

    public void add(Long tripId, WebSocketSession session) {
        sessionsByTripId.computeIfAbsent(tripId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(Long tripId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByTripId.get(tripId);
        if (sessions == null) {
            return;
        }

        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByTripId.remove(tripId);
        }
    }

    public void broadcast(Long tripId, String payload) {
        Set<WebSocketSession> sessions = sessionsByTripId.get(tripId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                remove(tripId, session);
                continue;
            }

            try {
                session.sendMessage(message);
            } catch (IOException e) {
                remove(tripId, session);
            }
        }
    }
}
