package com.ssafy.wswg.realtime;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dto.PlanEditEvent;
import com.ssafy.wswg.model.dto.PlanSocketMessage;
import com.ssafy.wswg.util.JwtProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlanEditWebSocketHandler extends TextWebSocketHandler {
    private static final String TRIP_ID_ATTRIBUTE = "tripId";
    private static final String USER_ID_ATTRIBUTE = "userId";
    private static final String BEARER_PREFIX = "Bearer ";

    private final PlanEditSessionRegistry sessionRegistry;
    private final PlanStateService planStateService;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long tripId = resolveTripId(session);
        Long userId = resolveUserId(session);
        JsonNode state = planStateService.loadState(tripId, userId);

        session.getAttributes().put(TRIP_ID_ATTRIBUTE, tripId);
        session.getAttributes().put(USER_ID_ATTRIBUTE, userId);
        sessionRegistry.add(tripId, session);

        send(session, planStateService.syncMessage(tripId, state));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long tripId = getRequiredAttribute(session, TRIP_ID_ATTRIBUTE);
        Long userId = getRequiredAttribute(session, USER_ID_ATTRIBUTE);
        PlanEditEvent event = objectMapper.readValue(message.getPayload(), PlanEditEvent.class);

        try {
            send(session, planStateService.applyEdit(tripId, userId, event));
        } catch (CommonException e) {
            // 편집 처리 실패(BUSY/잘못된 JSON 등)는 소켓을 끊지 말고 구조화된 error 로 회신.
            // 클라이언트는 clientOpId·code 로 재시도(BUSY)/폐기(그 외)를 판단한다.
            send(session, planStateService.errorMessage(tripId, event, e));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object tripId = session.getAttributes().get(TRIP_ID_ATTRIBUTE);
        if (tripId instanceof Long id) {
            sessionRegistry.remove(id, session);
            clearPresence(session, id);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Object tripId = session.getAttributes().get(TRIP_ID_ATTRIBUTE);
        if (tripId instanceof Long id) {
            sessionRegistry.remove(id, session);
            clearPresence(session, id);
        }
    }

    private Long resolveTripId(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        String[] parts = path.split("/");
        if (parts.length == 4 && "ws".equals(parts[1]) && "plans".equals(parts[2])) {
            return Long.valueOf(parts[3]);
        }

        throw new CommonException(ErrorCode.NOT_FOUND_TRIP);
    }

    private Long resolveUserId(WebSocketSession session) {
        String token = resolveToken(session);
        if (token == null || !jwtProvider.validateToken(token) || !"access".equals(jwtProvider.getTokenTypeFromToken(token))) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }

        return jwtProvider.getUserIdFromToken(token);
    }

    private String resolveToken(WebSocketSession session) {
        List<String> authorization = session.getHandshakeHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isEmpty() && authorization.get(0).startsWith(BEARER_PREFIX)) {
            return authorization.get(0).substring(BEARER_PREFIX.length());
        }

        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }

        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("token");
    }

    @SuppressWarnings("unchecked")
    private <T> T getRequiredAttribute(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        if (value == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }

        return (T) value;
    }

    private void send(WebSocketSession session, PlanSocketMessage message) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }

    private void clearPresence(WebSocketSession session, Long tripId) {
        Object userId = session.getAttributes().get(USER_ID_ATTRIBUTE);
        if (userId instanceof Long id) {
            planStateService.clearPresence(tripId, id);
        }
    }
}
