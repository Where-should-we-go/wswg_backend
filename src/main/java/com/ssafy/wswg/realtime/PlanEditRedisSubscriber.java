package com.ssafy.wswg.realtime;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class PlanEditRedisSubscriber implements MessageListener {
    private static final Pattern CHANNEL_PATTERN = Pattern.compile("^plan:(\\d+):edit$");

    private final PlanEditSessionRegistry sessionRegistry;

    public PlanEditRedisSubscriber(PlanEditSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        Matcher matcher = CHANNEL_PATTERN.matcher(channel);
        if (!matcher.matches()) {
            return;
        }

        Long tripId = Long.valueOf(matcher.group(1));
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        sessionRegistry.broadcast(tripId, payload);
    }
}
