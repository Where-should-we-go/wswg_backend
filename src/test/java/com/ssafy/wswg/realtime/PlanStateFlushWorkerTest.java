package com.ssafy.wswg.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

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
    void flushDuePlansKeepsDirtyMarkerWhenScoreChangedDuringSave() {
        redisTemplate.values.put("plan:10:state", "{\"items\":[]}");
        redisTemplate.zSets.computeIfAbsent("plan:dirty", ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .put("10", 1D);

        RecordingTripDao tripDao = new RecordingTripDao(redisTemplate, true);
        PlanStateFlushWorker flushWorker = new PlanStateFlushWorker(redisTemplate, planStateService, tripDao);

        flushWorker.flushDuePlans();

        assertThat(tripDao.updatedTripId).isEqualTo(10L);
        assertThat(redisTemplate.zSets.get("plan:dirty")).containsKey("10");
        assertThat(redisTemplate.deletedKeys).contains("plan:10:flushLock");
    }

    @Test
    void flushDuePlansRemovesDirtyMarkerAfterSuccessfulSaveWhenScoreIsUnchanged() {
        redisTemplate.values.put("plan:10:state", "{\"items\":[]}");
        redisTemplate.zSets.computeIfAbsent("plan:dirty", ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .put("10", 1D);

        RecordingTripDao tripDao = new RecordingTripDao(redisTemplate, false);
        PlanStateFlushWorker flushWorker = new PlanStateFlushWorker(redisTemplate, planStateService, tripDao);

        flushWorker.flushDuePlans();

        assertThat(tripDao.updatedTripId).isEqualTo(10L);
        assertThat(redisTemplate.zSets.get("plan:dirty")).doesNotContainKey("10");
    }

    private static class FakePlanStateService extends PlanStateService {
        FakePlanStateService(FakeStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
            super(redisTemplate, objectMapper, null);
        }

        @Override
        public String dirtyZSetKey() {
            return "plan:dirty";
        }

        @Override
        public Duration flushLockTtl() {
            return Duration.ofSeconds(10);
        }

        @Override
        public String stateKey(Long tripId) {
            return "plan:%d:state".formatted(tripId);
        }
    }

    private static class RecordingTripDao implements TripDao {
        private final FakeStringRedisTemplate redisTemplate;
        private final boolean changeScoreDuringSave;
        private Long updatedTripId;

        RecordingTripDao(FakeStringRedisTemplate redisTemplate, boolean changeScoreDuringSave) {
            this.redisTemplate = redisTemplate;
            this.changeScoreDuringSave = changeScoreDuringSave;
        }

        @Override
        public int updateTripData(Long tripId, JsonNode data) {
            updatedTripId = tripId;
            if (changeScoreDuringSave) {
                redisTemplate.zSets.get("plan:dirty").put(String.valueOf(tripId), System.currentTimeMillis() + 5_000D);
            }
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
