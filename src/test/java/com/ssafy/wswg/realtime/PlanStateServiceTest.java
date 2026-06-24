package com.ssafy.wswg.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.wswg.model.dto.PlanEditEvent;
import com.ssafy.wswg.model.dto.PlanEditType;
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
    void applyEdit_updatesRedisStatePublishesEventAndSchedulesFlush() throws Exception {
        redisTemplate.values.put("plan:10:state", "{\"items\":[{\"id\":\"item-1\",\"title\":\"이전 제목\",\"order\":1}]}");
        redisTemplate.sequences.put("plan:10:seq", 2L);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("patch", objectMapper.createObjectNode().put("title", "새 제목"));

        PlanEditEvent event = new PlanEditEvent();
        event.setType(PlanEditType.EDIT_UPDATE);
        event.setItemId("item-1");
        event.setPayload(payload);

        PlanEditEvent appliedEvent = planStateService.applyEdit(TRIP_ID, USER_ID, event);

        assertThat(appliedEvent.getTripId()).isEqualTo(TRIP_ID);
        assertThat(appliedEvent.getActorId()).isEqualTo(USER_ID);
        assertThat(appliedEvent.getSeq()).isEqualTo(3L);
        assertThat(appliedEvent.getEventId()).isNotBlank();
        assertThat(appliedEvent.getCreatedAt()).isNotNull();

        JsonNode nextState = objectMapper.readTree(redisTemplate.values.get("plan:10:state"));
        assertThat(nextState.get("items").get(0).get("title").asText()).isEqualTo("새 제목");
        assertThat(redisTemplate.zSets.get("plan:dirty").get("10")).isGreaterThan(System.currentTimeMillis());
        assertThat(redisTemplate.publishedChannel).isEqualTo("plan:10:edit");
        assertThat(redisTemplate.publishedMessage).contains("\"type\":\"EDIT\"");
        assertThat(redisTemplate.deletedKeys).contains("plan:10:editLock");
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
