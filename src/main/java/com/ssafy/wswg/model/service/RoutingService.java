package com.ssafy.wswg.model.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.external.route.NaverDirectionsClient;
import com.ssafy.wswg.external.route.OdsayTransitClient;
import com.ssafy.wswg.model.dto.RouteLeg;
import com.ssafy.wswg.model.dto.TravelLegsRequest;
import com.ssafy.wswg.model.dto.TravelMode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 두 지점 사이 이동 구간(거리/시간)을 이동수단에 맞게 계산한다.
 *
 * <p>같은 출발-도착 쌍은 반복되므로 Redis에 캐시해 외부 호출 비용/지연을 줄인다. 이동시간은
 * 부가정보라 길찾기·캐시 어떤 실패도 예외로 전파하지 않는다(여행 생성이 막히면 안 됨).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String CACHE_KEY_FORMAT = "route:%s:%.5f,%.5f:%.5f,%.5f";
    private static final Duration CACHE_TTL = Duration.ofDays(30);

    private final NaverDirectionsClient naverDirectionsClient;
    private final OdsayTransitClient odsayTransitClient;
    private final StringRedisTemplate stringRedisTemplate;

    /** 좌표쌍 목록을 순서대로 계산한다. 좌표가 빠진 구간은 unavailable로 채워 인덱스를 보존한다. */
    public List<RouteLeg> legs(TravelMode mode, List<TravelLegsRequest.Leg> legs) {
        TravelMode resolved = TravelMode.fromNullable(mode);
        List<RouteLeg> results = new ArrayList<>();
        if (legs == null) {
            return results;
        }

        for (TravelLegsRequest.Leg leg : legs) {
            if (leg == null || leg.getFromLat() == null || leg.getFromLng() == null
                    || leg.getToLat() == null || leg.getToLng() == null) {
                results.add(RouteLeg.unavailable(resolved, "missing-coordinate"));
                continue;
            }
            results.add(leg(resolved, leg.getFromLat(), leg.getFromLng(), leg.getToLat(), leg.getToLng()));
        }

        return results;
    }

    public RouteLeg leg(TravelMode mode, double fromLat, double fromLng, double toLat, double toLng) {
        TravelMode resolved = TravelMode.fromNullable(mode);
        String cacheKey = cacheKey(resolved, fromLat, fromLng, toLat, toLng);

        RouteLeg cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        RouteLeg leg = resolved == TravelMode.TRANSIT
                ? odsayTransitClient.transit(fromLat, fromLng, toLat, toLng)
                : naverDirectionsClient.drive(fromLat, fromLng, toLat, toLng);

        if (leg.isAvailable()) {
            writeCache(cacheKey, leg);
        }

        return leg;
    }

    private RouteLeg readCache(String cacheKey) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return null;
            }
            return OBJECT_MAPPER.readValue(json, RouteLeg.class);
        } catch (Exception e) {
            log.warn("이동 구간 캐시 조회 실패 ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    private void writeCache(String cacheKey, RouteLeg leg) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, OBJECT_MAPPER.writeValueAsString(leg), CACHE_TTL);
        } catch (Exception e) {
            log.warn("이동 구간 캐시 저장 실패 ({})", e.getClass().getSimpleName());
        }
    }

    private String cacheKey(TravelMode mode, double fromLat, double fromLng, double toLat, double toLng) {
        return String.format(Locale.ROOT, CACHE_KEY_FORMAT, mode.name(), fromLat, fromLng, toLat, toLng);
    }
}
