package com.ssafy.wswg.realtime;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.wswg.model.dao.TripDao;

@Component
public class PlanStateFlushWorker {
    private static final int FLUSH_BATCH_SIZE = 100;
    private static final String LOCK_KEY_FORMAT = "plan:%s:flushLock";
    private static final String INITIAL_STREAM_ID = "0-0";

    private final StringRedisTemplate stringRedisTemplate;
    private final PlanStateService planStateService;
    private final TripDao tripDao;
    private final String workerId = UUID.randomUUID().toString();

    public PlanStateFlushWorker(
            StringRedisTemplate stringRedisTemplate,
            PlanStateService planStateService,
            TripDao tripDao) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.planStateService = planStateService;
        this.tripDao = tripDao;
    }

    @Scheduled(fixedDelay = 10_000L)
    public void flushDuePlans() {
        Set<String> tripIds = stringRedisTemplate.opsForSet().members(planStateService.streamsKey());
        if (tripIds == null || tripIds.isEmpty()) {
            return;
        }

        for (String tripId : tripIds) {
            flushTrip(tripId);
        }
    }

    private void flushTrip(String tripId) {
        String lockKey = LOCK_KEY_FORMAT.formatted(tripId);
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, workerId, planStateService.flushLockTtl());
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        try {
            Long tripIdValue = Long.valueOf(tripId);
            String lastFlushedIdKey = planStateService.lastFlushedIdKey(tripIdValue);
            String lastFlushedId = stringRedisTemplate.opsForValue().get(lastFlushedIdKey);
            if (lastFlushedId == null || lastFlushedId.isBlank()) {
                lastFlushedId = INITIAL_STREAM_ID;
            }

            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                    .range(planStateService.streamKey(tripIdValue),
                            Range.leftOpen(lastFlushedId, "+"),
                            Limit.limit().count(FLUSH_BATCH_SIZE));
            if (records == null || records.isEmpty()) {
                return;
            }

            String stateJson = stringRedisTemplate.opsForValue().get(planStateService.stateKey(Long.valueOf(tripId)));
            if (stateJson == null) {
                return;
            }

            JsonNode state = planStateService.readJson(stateJson);
            tripDao.updateTripData(tripIdValue, state);
            String flushedId = records.get(records.size() - 1).getId().getValue();
            stringRedisTemplate.opsForValue().set(lastFlushedIdKey, flushedId);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }
}
