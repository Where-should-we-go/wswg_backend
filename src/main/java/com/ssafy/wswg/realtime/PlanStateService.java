package com.ssafy.wswg.realtime;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
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
import com.ssafy.wswg.model.dto.PlanSocketMessage;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.service.TripService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlanStateService {
    private static final String STATE_KEY_FORMAT = "plan:%d:state";
    private static final String EDIT_CHANNEL_FORMAT = "plan:%d:edit";
    private static final String STREAM_KEY_FORMAT = "plan:%d:stream";
    private static final String LAST_FLUSHED_ID_KEY_FORMAT = "plan:%d:lastFlushedId";
    private static final String PRESENCE_KEY_FORMAT = "plan:%d:presence";
    private static final String EDIT_LOCK_KEY_FORMAT = "plan:%d:editLock";
    private static final String STREAMS_KEY = "plan:streams";
    private static final String INITIAL_STREAM_ID = "0-0";
    private static final Duration EDIT_LOCK_TTL = Duration.ofSeconds(3);
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(30);
    private static final String TYPE_BLOCK_ADD = "block.add";
    private static final String TYPE_BLOCK_UPDATE = "block.update";
    private static final String TYPE_BLOCK_REMOVE = "block.remove";
    private static final String TYPE_META_UPDATE = "meta.update";
    private static final String TYPE_PRESENCE = "presence";

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

    public PlanSocketMessage syncMessage(Long tripId, JsonNode data) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("data", data);
        payload.set("presence", presenceSnapshot(tripId));
        payload.put("lastSeq", lastSeq(tripId));
        return message("sync", tripId, null, null, payload);
    }

    public PlanSocketMessage applyEdit(Long tripId, Long userId, PlanEditEvent event) {
        tripService.readTrip(tripId, userId);

        String lockKey = editLockKey(tripId);
        acquireEditLock(lockKey);

        try {
            String type = event.getType();
            if (type == null || type.isBlank()) {
                throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
            }

            if (TYPE_PRESENCE.equals(type)) {
                PlanSocketMessage presenceMessage = applyPresence(tripId, userId, event);
                stringRedisTemplate.convertAndSend(editChannel(tripId), writeJson(presenceMessage));
                return ack(tripId, null);
            }

            JsonNode state = loadState(tripId, userId);
            JsonNode nextState = mutateState(state, event);
            if (nextState == null) {
                return error(tripId, "STALE", "대상 블록이 이미 삭제되었거나 존재하지 않습니다.");
            }

            OffsetDateTime ts = OffsetDateTime.now();
            JsonNode actor = actor(userId);
            String seq = appendStream(tripId, type, actor, ts, event.getPayload());
            stringRedisTemplate.opsForSet().add(STREAMS_KEY, String.valueOf(tripId));

            stringRedisTemplate.opsForValue().set(stateKey(tripId), writeJson(nextState));
            PlanSocketMessage message = message(type, tripId, actor, seq, ts, event.getPayload());
            stringRedisTemplate.convertAndSend(editChannel(tripId), writeJson(message));

            return ack(tripId, seq);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    public void clearPresence(Long tripId, Long userId) {
        String presenceKey = PRESENCE_KEY_FORMAT.formatted(tripId);
        stringRedisTemplate.opsForHash().delete(presenceKey, String.valueOf(userId));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.putNull("blockId");
        PlanSocketMessage message = message(TYPE_PRESENCE, tripId, actor(userId), null, OffsetDateTime.now(), payload);
        stringRedisTemplate.convertAndSend(editChannel(tripId), writeJson(message));
    }

    public String stateKey(Long tripId) {
        return STATE_KEY_FORMAT.formatted(tripId);
    }

    public String streamKey(Long tripId) {
        return STREAM_KEY_FORMAT.formatted(tripId);
    }

    public String streamsKey() {
        return STREAMS_KEY;
    }

    public String lastFlushedIdKey(Long tripId) {
        return LAST_FLUSHED_ID_KEY_FORMAT.formatted(tripId);
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
        String type = event.getType();

        if (TYPE_BLOCK_ADD.equals(type)) {
            JsonNode item = event.getPayload() != null && event.getPayload().has("block")
                    ? event.getPayload().get("block")
                    : event.getPayload();
            if (item == null || !item.isObject()) {
                throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
            }
            items.add(item);
            return nextState;
        }

        if (TYPE_BLOCK_UPDATE.equals(type)) {
            String itemId = payloadText(event.getPayload(), "id");
            ObjectNode item = findItemOrNull(items, itemId);
            if (item == null) {
                return null;
            }
            JsonNode patch = event.getPayload() != null && event.getPayload().has("patch")
                    ? event.getPayload().get("patch")
                    : null;
            if (patch == null || !patch.isObject()) {
                throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
            }
            mergeBlockPatch(item, (ObjectNode) patch);
            return nextState;
        }

        if (TYPE_BLOCK_REMOVE.equals(type)) {
            String itemId = payloadText(event.getPayload(), "id");
            return removeItem(items, itemId) ? nextState : null;
        }

        if (TYPE_META_UPDATE.equals(type)) {
            JsonNode patch = event.getPayload() == null ? null : event.getPayload().get("patch");
            if (patch == null || !patch.isObject()) {
                throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
            }
            ObjectNode meta = ensureObject(nextState, "meta");
            patch.fields().forEachRemaining(entry -> meta.set(entry.getKey(), entry.getValue()));
            return nextState;
        }

        throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
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

    private ObjectNode ensureObject(ObjectNode state, String fieldName) {
        JsonNode value = state.get(fieldName);
        if (value instanceof ObjectNode objectNode) {
            return objectNode;
        }

        ObjectNode objectNode = objectMapper.createObjectNode();
        state.set(fieldName, objectNode);
        return objectNode;
    }

    private ObjectNode findItemOrNull(ArrayNode items, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
        }

        for (JsonNode item : items) {
            if (item instanceof ObjectNode objectNode && itemId.equals(item.path("id").asText(null))) {
                return objectNode;
            }
        }

        return null;
    }

    private boolean removeItem(ArrayNode items, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
        }

        Iterator<JsonNode> iterator = items.iterator();
        while (iterator.hasNext()) {
            JsonNode item = iterator.next();
            if (itemId.equals(item.path("id").asText(null))) {
                iterator.remove();
                return true;
            }
        }

        return false;
    }

    private void mergeBlockPatch(ObjectNode item, ObjectNode patch) {
        patch.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if ("properties".equals(key)) {
                mergeProperties(item, value);
                return;
            }

            item.set(key, value);
        });
    }

    private void mergeProperties(ObjectNode item, JsonNode value) {
        if (value == null || value.isNull()) {
            item.set("properties", objectMapper.createObjectNode());
            return;
        }

        if (!value.isObject()) {
            throw new CommonException(ErrorCode.BAD_REQUEST_JSON);
        }

        ObjectNode properties = ensureObject(item, "properties");
        value.fields().forEachRemaining(entry -> {
            JsonNode propertyValue = entry.getValue();
            if (propertyValue == null || propertyValue.isNull()
                    || (propertyValue.isTextual() && propertyValue.asText().isBlank())) {
                properties.remove(entry.getKey());
                return;
            }
            properties.set(entry.getKey(), propertyValue);
        });
    }

    private String appendStream(Long tripId, String type, JsonNode actor, OffsetDateTime ts, JsonNode payload) {
        Map<String, String> value = Map.of(
                "type", type,
                "actor", writeJson(actor),
                "ts", ts.toString(),
                "payload", writeJson(payload == null ? objectMapper.createObjectNode() : payload));
        RecordId recordId = stringRedisTemplate.opsForStream().add(streamKey(tripId), value);
        if (recordId == null) {
            throw new CommonException(ErrorCode.PLAN_EDIT_BUSY);
        }
        return recordId.getValue();
    }

    private PlanSocketMessage applyPresence(Long tripId, Long userId, PlanEditEvent event) {
        String blockId = payloadText(event.getPayload(), "blockId");
        if (blockId == null || blockId.isBlank()) {
            stringRedisTemplate.opsForHash().delete(PRESENCE_KEY_FORMAT.formatted(tripId), String.valueOf(userId));
        } else {
            String presenceKey = PRESENCE_KEY_FORMAT.formatted(tripId);
            stringRedisTemplate.opsForHash().put(presenceKey, String.valueOf(userId), blockId);
            stringRedisTemplate.expire(presenceKey, PRESENCE_TTL);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        if (blockId == null) {
            payload.putNull("blockId");
        } else {
            payload.put("blockId", blockId);
        }
        return message(TYPE_PRESENCE, tripId, actor(userId), null, OffsetDateTime.now(), payload);
    }

    private ArrayNode presenceSnapshot(Long tripId) {
        ArrayNode presence = objectMapper.createArrayNode();
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(PRESENCE_KEY_FORMAT.formatted(tripId));
        entries.forEach((memberId, blockId) -> {
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("memberId", String.valueOf(memberId));
            entry.put("blockId", String.valueOf(blockId));
            presence.add(entry);
        });
        return presence;
    }

    private String lastSeq(Long tripId) {
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .reverseRange(streamKey(tripId), Range.unbounded(), Limit.limit().count(1));
        if (records == null || records.isEmpty()) {
            return INITIAL_STREAM_ID;
        }
        return records.get(0).getId().getValue();
    }

    private JsonNode actor(Long userId) {
        ObjectNode actor = objectMapper.createObjectNode();
        actor.put("userId", userId);
        return actor;
    }

    private PlanSocketMessage ack(Long tripId, String seq) {
        return message("ack", tripId, null, seq, objectMapper.createObjectNode());
    }

    private PlanSocketMessage error(Long tripId, String code, String errorMessage) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("code", code);
        payload.put("message", errorMessage);
        return message("error", tripId, null, null, payload);
    }

    private PlanSocketMessage message(String type, Long tripId, JsonNode actor, String seq, JsonNode payload) {
        return message(type, tripId, actor, seq, OffsetDateTime.now(), payload);
    }

    private PlanSocketMessage message(String type, Long tripId, JsonNode actor, String seq, OffsetDateTime ts, JsonNode payload) {
        return new PlanSocketMessage(type, tripId, actor, seq, ts, payload);
    }

    private String payloadText(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return null;
        }
        return payload.get(fieldName).asText();
    }

    private String editChannel(Long tripId) {
        return EDIT_CHANNEL_FORMAT.formatted(tripId);
    }

    private String editLockKey(Long tripId) {
        return EDIT_LOCK_KEY_FORMAT.formatted(tripId);
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
