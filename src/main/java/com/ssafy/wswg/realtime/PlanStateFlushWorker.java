package com.ssafy.wswg.realtime;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.wswg.model.dao.TripDao;

@Component
public class PlanStateFlushWorker {
    private static final int FLUSH_BATCH_SIZE = 100;
    private static final String LOCK_KEY_FORMAT = "plan:%s:flushLock";

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

    @Scheduled(fixedDelay = 1_000L)
    public void flushDuePlans() {
        long now = System.currentTimeMillis();
        Set<String> dueTripIds = stringRedisTemplate.opsForZSet()
                .rangeByScore(planStateService.dirtyZSetKey(), 0, now, 0, FLUSH_BATCH_SIZE);

        if (dueTripIds == null || dueTripIds.isEmpty()) {
            return;
        }

        for (String tripId : dueTripIds) {
            flushTrip(tripId, now);
        }
    }

    private void flushTrip(String tripId, long now) {
        String lockKey = LOCK_KEY_FORMAT.formatted(tripId);
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, workerId, planStateService.flushLockTtl());
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        Double observedScore = stringRedisTemplate.opsForZSet().score(planStateService.dirtyZSetKey(), tripId);
        if (observedScore == null || observedScore > now) {
            stringRedisTemplate.delete(lockKey);
            return;
        }

        try {
            String stateJson = stringRedisTemplate.opsForValue().get(planStateService.stateKey(Long.valueOf(tripId)));
            if (stateJson == null) {
                removeDirtyIfUnchanged(tripId, observedScore);
                return;
            }

            JsonNode state = planStateService.readJson(stateJson);
            tripDao.updateTripData(Long.valueOf(tripId), state);
            removeDirtyIfUnchanged(tripId, observedScore);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void removeDirtyIfUnchanged(String tripId, Double observedScore) {
        Double currentScore = stringRedisTemplate.opsForZSet().score(planStateService.dirtyZSetKey(), tripId);
        if (Objects.equals(observedScore, currentScore)) {
            stringRedisTemplate.opsForZSet().remove(planStateService.dirtyZSetKey(), tripId);
        }
    }
}
