package com.ssafy.wswg.realtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

class FakeStringRedisTemplate extends StringRedisTemplate {
    final Map<String, String> values = new ConcurrentHashMap<>();
    final Map<String, Long> sequences = new ConcurrentHashMap<>();
    final Map<String, Map<String, Double>> zSets = new ConcurrentHashMap<>();
    final Map<String, java.util.Set<String>> sets = new ConcurrentHashMap<>();
    final Map<String, Map<Object, Object>> hashes = new ConcurrentHashMap<>();
    final Map<String, List<MapRecord<String, Object, Object>>> streams = new ConcurrentHashMap<>();
    final List<String> deletedKeys = new ArrayList<>();

    String publishedChannel;
    String publishedMessage;

    private final ValueOperations<String, String> valueOperations = proxy(ValueOperations.class, this::handleValueOperation);
    private final ZSetOperations<String, String> zSetOperations = proxy(ZSetOperations.class, this::handleZSetOperation);
    private final SetOperations<String, String> setOperations = proxy(SetOperations.class, this::handleSetOperation);
    private final HashOperations<String, Object, Object> hashOperations = proxy(HashOperations.class, this::handleHashOperation);
    private final StreamOperations<String, Object, Object> streamOperations = proxy(StreamOperations.class, this::handleStreamOperation);

    @Override
    public ValueOperations<String, String> opsForValue() {
        return valueOperations;
    }

    @Override
    public ZSetOperations<String, String> opsForZSet() {
        return zSetOperations;
    }

    @Override
    public SetOperations<String, String> opsForSet() {
        return setOperations;
    }

    @Override
    public <HK, HV> HashOperations<String, HK, HV> opsForHash() {
        @SuppressWarnings("unchecked")
        HashOperations<String, HK, HV> operations = (HashOperations<String, HK, HV>) hashOperations;
        return operations;
    }

    @Override
    public <HK, HV> StreamOperations<String, HK, HV> opsForStream() {
        @SuppressWarnings("unchecked")
        StreamOperations<String, HK, HV> operations = (StreamOperations<String, HK, HV>) streamOperations;
        return operations;
    }

    @Override
    public Long convertAndSend(String channel, Object message) {
        this.publishedChannel = channel;
        this.publishedMessage = String.valueOf(message);
        return 1L;
    }

    @Override
    public Boolean delete(String key) {
        deletedKeys.add(key);
        values.remove(key);
        return true;
    }

    @Override
    public Boolean expire(String key, long timeout, java.util.concurrent.TimeUnit unit) {
        // TTL 은 테스트에서 의미 없음 — no-op(실 Redis 연결 회피).
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private Object handleValueOperation(Object proxy, java.lang.reflect.Method method, Object[] args) {
        String methodName = method.getName();
        if ("get".equals(methodName)) {
            return values.get((String) args[0]);
        }

        if ("set".equals(methodName)) {
            values.put((String) args[0], (String) args[1]);
            return null;
        }

        if ("increment".equals(methodName)) {
            return sequences.merge((String) args[0], 1L, Long::sum);
        }

        if ("setIfAbsent".equals(methodName)) {
            return values.putIfAbsent((String) args[0], (String) args[1]) == null;
        }

        return defaultValue(method.getReturnType());
    }

    private Object handleSetOperation(Object proxy, java.lang.reflect.Method method, Object[] args) {
        String methodName = method.getName();
        if ("add".equals(methodName)) {
            java.util.Set<String> set = sets.computeIfAbsent((String) args[0], ignored -> ConcurrentHashMap.newKeySet());
            Object[] valuesToAdd = args[1] instanceof Object[] array ? array : new Object[] { args[1] };
            long added = 0L;
            for (Object value : valuesToAdd) {
                added += set.add((String) value) ? 1 : 0;
            }
            return added;
        }

        if ("members".equals(methodName)) {
            return new LinkedHashSet<>(sets.getOrDefault((String) args[0], java.util.Set.of()));
        }

        if ("remove".equals(methodName)) {
            java.util.Set<String> set = sets.get((String) args[0]);
            if (set == null) {
                return 0L;
            }
            Object[] valuesToRemove = args[1] instanceof Object[] array ? array : new Object[] { args[1] };
            long removed = 0L;
            for (Object value : valuesToRemove) {
                removed += set.remove((String) value) ? 1 : 0;
            }
            return removed;
        }

        return defaultValue(method.getReturnType());
    }

    private Object handleHashOperation(Object proxy, java.lang.reflect.Method method, Object[] args) {
        String methodName = method.getName();
        if ("put".equals(methodName)) {
            hashes.computeIfAbsent((String) args[0], ignored -> new ConcurrentHashMap<>()).put(args[1], args[2]);
            return null;
        }

        if ("delete".equals(methodName)) {
            Map<Object, Object> hash = hashes.get((String) args[0]);
            if (hash == null) {
                return 0L;
            }
            Object[] valuesToRemove = args[1] instanceof Object[] array ? array : new Object[] { args[1] };
            long removed = 0L;
            for (Object value : valuesToRemove) {
                removed += hash.remove(value) == null ? 0 : 1;
            }
            return removed;
        }

        if ("entries".equals(methodName)) {
            return new HashMap<>(hashes.getOrDefault((String) args[0], Map.of()));
        }

        return defaultValue(method.getReturnType());
    }

    @SuppressWarnings("unchecked")
    private Object handleStreamOperation(Object proxy, java.lang.reflect.Method method, Object[] args) {
        String methodName = method.getName();
        if ("add".equals(methodName)) {
            String streamKey = (String) args[0];
            Map<Object, Object> value = new HashMap<>((Map<Object, Object>) args[1]);
            String id = System.currentTimeMillis() + "-" + streams.computeIfAbsent(streamKey, ignored -> new ArrayList<>()).size();
            streams.get(streamKey).add(MapRecord.create(streamKey, value).withId(RecordId.of(id)));
            return RecordId.of(id);
        }

        if ("range".equals(methodName)) {
            return new ArrayList<>(streams.getOrDefault((String) args[0], List.of()));
        }

        if ("reverseRange".equals(methodName)) {
            List<MapRecord<String, Object, Object>> records = new ArrayList<>(streams.getOrDefault((String) args[0], List.of()));
            java.util.Collections.reverse(records);
            if (records.size() > 1) {
                return records.subList(0, 1);
            }
            return records;
        }

        return defaultValue(method.getReturnType());
    }

    private Object handleZSetOperation(Object proxy, java.lang.reflect.Method method, Object[] args) {
        String methodName = method.getName();
        if ("add".equals(methodName)) {
            zSets.computeIfAbsent((String) args[0], ignored -> new ConcurrentHashMap<>())
                    .put((String) args[1], ((Number) args[2]).doubleValue());
            return true;
        }

        if ("rangeByScore".equals(methodName)) {
            Map<String, Double> zSet = zSets.getOrDefault((String) args[0], Map.of());
            double min = ((Number) args[1]).doubleValue();
            double max = ((Number) args[2]).doubleValue();
            long offset = ((Number) args[3]).longValue();
            long count = ((Number) args[4]).longValue();
            return zSet.entrySet().stream()
                    .filter(entry -> entry.getValue() >= min && entry.getValue() <= max)
                    .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                    .skip(offset)
                    .limit(count)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        if ("score".equals(methodName)) {
            return zSets.getOrDefault((String) args[0], Map.of()).get((String) args[1]);
        }

        if ("remove".equals(methodName)) {
            Map<String, Double> zSet = zSets.get((String) args[0]);
            if (zSet == null) {
                return 0L;
            }

            long removed = 0L;
            Object[] valuesToRemove = args[1] instanceof Object[] array ? array : new Object[] { args[1] };
            for (Object value : valuesToRemove) {
                removed += zSet.remove((String) value) == null ? 0 : 1;
            }
            return removed;
        }

        return defaultValue(method.getReturnType());
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
