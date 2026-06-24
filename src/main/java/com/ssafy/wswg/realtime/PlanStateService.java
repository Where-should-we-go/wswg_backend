package com.ssafy.wswg.realtime;

import java.time.Duration;
import java.util.Iterator;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dto.PlanEditEvent;
import com.ssafy.wswg.model.dto.PlanEditType;
import com.ssafy.wswg.model.dto.PlanSocketMessage;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.service.TripService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlanStateService {
    private static final String STATE_KEY_FORMAT = "plan:%d:state";
    private static final String EDIT_CHANNEL_FORMAT = "plan:%d:edit";
    private static final String SEQ_KEY_FORMAT = "plan:%d:seq";
    private static final String EDIT_LOCK_KEY_FORMAT = "plan:%d:editLock";
    private static final String DIRTY_ZSET_KEY = "plan:dirty";
    private static final long DEBOUNCE_MILLIS = 5_000L;
    private static final Duration EDIT_LOCK_TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TripService tripService;

    public JsonNode loadState(Long tripId, Long userId) {
        TripDto trip = tripService.readTrip(tripId, userId);
        String stateKey = stateKey(tripId);
        String stateJson = stringRedisTemplate.opsForValue().get(stateKey);

        if (stateJson != null) {
            return readJson(stateJson);
        }

        JsonNode state = normalizeState(trip.getData());
        stringRedisTemplate.opsForValue().set(stateKey, writeJson(state));
        return state;
    }

    public PlanEditEvent applyEdit(Long tripId, Long userId, PlanEditEvent event) {
        tripService.readTrip(tripId, userId);

        String lockKey = editLockKey(tripId);
        acquireEditLock(lockKey);

        try {
            Long seq = stringRedisTemplate.opsForValue().increment(seqKey(tripId));
            event.prepare(tripId, userId, seq == null ? 1L : seq);

            if (event.getType() == null) {
                throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
            }

            JsonNode state = loadState(tripId, userId);
            JsonNode nextState = mutateState(state, event);
            stringRedisTemplate.opsForValue().set(stateKey(tripId), writeJson(nextState));
            scheduleFlush(tripId);
            PlanSocketMessage message = new PlanSocketMessage(
                    "EDIT",
                    tripId,
                    event.getSeq(),
                    objectMapper.valueToTree(event));
            stringRedisTemplate.convertAndSend(editChannel(tripId), writeJson(message));

            return event;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    public String stateKey(Long tripId) {
        return STATE_KEY_FORMAT.formatted(tripId);
    }

    public String dirtyZSetKey() {
        return DIRTY_ZSET_KEY;
    }

    public void scheduleFlush(Long tripId) {
        long flushAt = System.currentTimeMillis() + DEBOUNCE_MILLIS;
        stringRedisTemplate.opsForZSet().add(DIRTY_ZSET_KEY, String.valueOf(tripId), flushAt);
    }

    public Duration flushLockTtl() {
        return Duration.ofSeconds(10);
    }

    public JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
        }
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
        }
    }

    private JsonNode mutateState(JsonNode state, PlanEditEvent event) {
        ObjectNode nextState = normalizeState(state).deepCopy();
        ArrayNode items = ensureItems(nextState);
        PlanEditType type = event.getType();

        if (type == PlanEditType.EDIT_ADD) {
            JsonNode item = event.getPayload() != null && event.getPayload().has("item")
                    ? event.getPayload().get("item")
                    : event.getPayload();
            if (item == null || !item.isObject()) {
                throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
            }
            items.add(item);
            return nextState;
        }

        if (type == PlanEditType.EDIT_UPDATE) {
            ObjectNode item = findItem(items, event.getItemId());
            JsonNode patch = event.getPayload() != null && event.getPayload().has("patch")
                    ? event.getPayload().get("patch")
                    : event.getPayload();
            if (patch == null || !patch.isObject()) {
                throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
            }
            patch.fields().forEachRemaining(entry -> item.set(entry.getKey(), entry.getValue()));
            return nextState;
        }

        if (type == PlanEditType.EDIT_DELETE) {
            removeItem(items, event.getItemId());
            return nextState;
        }

        if (type == PlanEditType.EDIT_REORDER) {
            reorderItems(items, event.getPayload());
            return nextState;
        }

        return nextState;
    }

    private ObjectNode normalizeState(JsonNode data) {
        ObjectNode normalized = data != null && data.isObject()
                ? (ObjectNode) data.deepCopy()
                : objectMapper.createObjectNode();
        ensureItems(normalized);
        return normalized;
    }

    private ArrayNode ensureItems(ObjectNode state) {
        JsonNode items = state.get("items");
        if (items instanceof ArrayNode arrayNode) {
            return arrayNode;
        }

        ArrayNode arrayNode = objectMapper.createArrayNode();
        state.set("items", arrayNode);
        return arrayNode;
    }

    private ObjectNode findItem(ArrayNode items, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
        }

        for (JsonNode item : items) {
            if (item instanceof ObjectNode objectNode && itemId.equals(item.path("id").asText(null))) {
                return objectNode;
            }
        }

        throw new CommonException(ErrorCode.NOT_FOUND_TRIP_ITEM);
    }

    private void removeItem(ArrayNode items, String itemId) {
        Iterator<JsonNode> iterator = items.iterator();
        while (iterator.hasNext()) {
            JsonNode item = iterator.next();
            if (itemId != null && itemId.equals(item.path("id").asText(null))) {
                iterator.remove();
                return;
            }
        }

        throw new CommonException(ErrorCode.NOT_FOUND_TRIP_ITEM);
    }

    private void reorderItems(ArrayNode items, JsonNode payload) {
        JsonNode orderedIds = payload == null ? null : payload.get("orderedItemIds");
        if (orderedIds == null || !orderedIds.isArray()) {
            throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
        }

        int order = 1;
        for (JsonNode orderedId : orderedIds) {
            ObjectNode item = findItem(items, orderedId.asText());
            item.put("order", order++);
            if (payload.hasNonNull("visitDate")) {
                item.put("visitDate", payload.get("visitDate").asText());
            }
        }
    }

    private String editChannel(Long tripId) {
        return EDIT_CHANNEL_FORMAT.formatted(tripId);
    }

    private String editLockKey(Long tripId) {
        return EDIT_LOCK_KEY_FORMAT.formatted(tripId);
    }

    private String seqKey(Long tripId) {
        return SEQ_KEY_FORMAT.formatted(tripId);
    }

    private void acquireEditLock(String lockKey) {
        for (int attempt = 0; attempt < 10; attempt++) {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", EDIT_LOCK_TTL);
            if (Boolean.TRUE.equals(locked)) {
                return;
            }

            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CommonException(ErrorCode.PLAN_EDIT_BUSY);
            }
        }

        throw new CommonException(ErrorCode.PLAN_EDIT_BUSY);
    }
}
