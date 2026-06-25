package com.ssafy.wswg.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripDto;

class PlanStateFlushWorkerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private FakeStringRedisTemplate redisTemplate;
    private FakePlanStateService planStateService;

    @BeforeEach
    void setUp() {
        redisTemplate = new FakeStringRedisTemplate();
        planStateService = new FakePlanStateService(redisTemplate, objectMapper);
    }

    @Test
    void flushDuePlansSkipsWhenStreamHasNoNewRecordAfterLastFlushedId() {
        redisTemplate.values.put("plan:10:state", "{\"items\":[]}");
        redisTemplate.sets.computeIfAbsent("plan:streams", ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add("10");
        redisTemplate.values.put("plan:10:lastFlushedId", "1-0");

        RecordingTripDao tripDao = new RecordingTripDao();
        PlanStateFlushWorker flushWorker = new PlanStateFlushWorker(redisTemplate, planStateService, tripDao);

        flushWorker.flushDuePlans();

        assertThat(tripDao.updatedTripId).isNull();
        assertThat(redisTemplate.deletedKeys).contains("plan:10:flushLock");
    }

    @Test
    void flushDuePlansReadsStreamAndStoresLastFlushedIdAfterSavingState() {
        redisTemplate.values.put("plan:10:state", "{\"items\":[]}");
        redisTemplate.sets.computeIfAbsent("plan:streams", ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add("10");
        redisTemplate.streams.computeIfAbsent("plan:10:stream", ignored -> new java.util.ArrayList<>())
                .add(MapRecord.create("plan:10:stream", java.util.Map.<Object, Object>of("type", "block.update"))
                        .withId(RecordId.of("1-0")));

        RecordingTripDao tripDao = new RecordingTripDao();
        PlanStateFlushWorker flushWorker = new PlanStateFlushWorker(redisTemplate, planStateService, tripDao);

        flushWorker.flushDuePlans();

        assertThat(tripDao.updatedTripId).isEqualTo(10L);
        assertThat(redisTemplate.values.get("plan:10:lastFlushedId")).isEqualTo("1-0");
    }

    private static class FakePlanStateService extends PlanStateService {
        FakePlanStateService(FakeStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
            super(redisTemplate, objectMapper, null);
        }

        @Override
        public Duration flushLockTtl() {
            return Duration.ofSeconds(10);
        }

        @Override
        public String stateKey(Long tripId) {
            return "plan:%d:state".formatted(tripId);
        }

        @Override
        public String streamKey(Long tripId) {
            return "plan:%d:stream".formatted(tripId);
        }

        @Override
        public String streamsKey() {
            return "plan:streams";
        }

        @Override
        public String lastFlushedIdKey(Long tripId) {
            return "plan:%d:lastFlushedId".formatted(tripId);
        }
    }

    private static class RecordingTripDao implements TripDao {
        private Long updatedTripId;

        @Override
        public int updateTripData(Long tripId, JsonNode data) {
            updatedTripId = tripId;
            return 1;
        }

        @Override
        public int updateTripMeta(Long tripId, String title, java.time.LocalDate startDate, java.time.LocalDate endDate) {
            return 1;
        }

        @Override
        public List<MyPageTripResponse> readMyTrips(Long userId, String status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MyPageTripResponse> readJoinedTrips(Long userId, String status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int createTrip(TripDto trip) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TripDto readTripById(Long tripId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TripDto> readTripsByUserId(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TripDto> readTripsByGroupId(Long groupId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateTrip(TripDto trip) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteTrip(Long tripId) {
            throw new UnsupportedOperationException();
        }
    }
}
