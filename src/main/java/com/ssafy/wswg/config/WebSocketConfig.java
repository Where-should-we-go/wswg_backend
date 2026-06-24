package com.ssafy.wswg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.ssafy.wswg.realtime.PlanEditWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final PlanEditWebSocketHandler planEditWebSocketHandler;

    public WebSocketConfig(PlanEditWebSocketHandler planEditWebSocketHandler) {
        this.planEditWebSocketHandler = planEditWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(planEditWebSocketHandler, "/ws/plans/{tripId}")
                .setAllowedOriginPatterns("*");
    }
}
