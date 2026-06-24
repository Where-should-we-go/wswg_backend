package com.ssafy.wswg.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.wswg.model.dto.PlanEditEvent;
import com.ssafy.wswg.model.dto.PlanSocketMessage;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.service.TripService;

class PlanStateServiceTest {
    private static final Long TRIP_ID = 10L;
    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private FakeStringRedisTemplate redisTemplate;
    private FakeTripService tripService;
    private PlanStateService planStateService;

    @BeforeEach
    void setUp() {
        redisTemplate = new FakeStringRedisTemplate();
        tripService = new FakeTripService();
        planStateService = new PlanStateService(redisTemplate, objectMapper, tripService);
    }

    @Test
    void loadState_readsFromTripDataAndCachesNormalizedStateWhenRedisIsEmpty() throws Exception {
        tripService.trip.setData(objectMapper.readTree("{\"memo\":\"첫 여행\"}"));

        JsonNode state = planStateService.loadState(TRIP_ID, USER_ID);

        assertThat(state.get("memo").asText()).isEqualTo("첫 여행");
        assertThat(state.get("items").isArray()).isTrue();
        assertThat(objectMapper.readTree(redisTemplate.values.get("plan:10:state")).get("items").isArray()).isTrue();
    }

    @Test
    void applyEdit_appendsStreamUpdatesRedisStateAndPublishesBlockEvent() throws Exception {
        redisTemplate.values.put("plan:10:state", "{\"items\":[{\"id\":\"item-1\",\"title\":\"이전 제목\",\"order\":1}]}");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", "item-1");
        payload.set("patch", objectMapper.createObjectNode().put("title", "새 제목"));

        PlanEditEvent event = new PlanEditEvent();
        event.setType("block.update");
        event.setPayload(payload);

        PlanSocketMessage ack = planStateService.applyEdit(TRIP_ID, USER_ID, event);

        assertThat(ack.getType()).isEqualTo("ack");
        assertThat(ack.getTripId()).isEqualTo(TRIP_ID);
        assertThat(ack.getSeq()).isNotBlank();

        JsonNode nextState = objectMapper.readTree(redisTemplate.values.get("plan:10:state"));
        assertThat(nextState.get("items").get(0).get("title").asText()).isEqualTo("새 제목");
        assertThat(redisTemplate.streams).containsKey("plan:10:stream");
        assertThat(redisTemplate.sets.get("plan:streams")).contains("10");
        assertThat(redisTemplate.publishedChannel).isEqualTo("plan:10:edit");
        assertThat(redisTemplate.publishedMessage).contains("\"type\":\"block.update\"");
        assertThat(redisTemplate.publishedMessage).contains("\"userId\":7");
        assertThat(redisTemplate.deletedKeys).contains("plan:10:editLock");
    }

    @Test
    void applyEdit_mergesPropertiesOneLevelDeeper() throws Exception {
        redisTemplate.values.put("plan:10:state",
                "{\"items\":[{\"id\":\"item-1\",\"properties\":{\"memo\":\"기존\",\"budget\":1000}}]}");

        ObjectNode properties = objectMapper.createObjectNode();
        properties.put("memo", "수정");
        properties.putNull("budget");

        ObjectNode patch = objectMapper.createObjectNode();
        patch.set("properties", properties);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", "item-1");
        payload.set("patch", patch);

        PlanEditEvent event = new PlanEditEvent();
        event.setType("block.update");
        event.setPayload(payload);

        planStateService.applyEdit(TRIP_ID, USER_ID, event);

        JsonNode item = objectMapper.readTree(redisTemplate.values.get("plan:10:state")).get("items").get(0);
        assertThat(item.get("properties").get("memo").asText()).isEqualTo("수정");
        assertThat(item.get("properties").has("budget")).isFalse();
    }

    @Test
    void applyEdit_returnsStaleErrorWhenBlockIsMissing() throws Exception {
        redisTemplate.values.put("plan:10:state", "{\"items\":[]}");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", "missing");
        payload.set("patch", objectMapper.createObjectNode().put("title", "새 제목"));

        PlanEditEvent event = new PlanEditEvent();
        event.setType("block.update");
        event.setPayload(payload);

        PlanSocketMessage error = planStateService.applyEdit(TRIP_ID, USER_ID, event);

        assertThat(error.getType()).isEqualTo("error");
        assertThat(error.getPayload().get("code").asText()).isEqualTo("STALE");
        assertThat(redisTemplate.streams).doesNotContainKey("plan:10:stream");
    }

    private static class FakeTripService extends TripService {
        private final TripDto trip = new TripDto();

        FakeTripService() {
            super(null, null);
            trip.setTripId(TRIP_ID);
        }

        @Override
        public TripDto readTrip(Long tripId, Long userId) {
            return trip;
        }
    }
}
