package com.ssafy.wswg.realtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

class FakeStringRedisTemplate extends StringRedisTemplate {
    final Map<String, String> values = new ConcurrentHashMap<>();
    final Map<String, Long> sequences = new ConcurrentHashMap<>();
    final Map<String, Map<String, Double>> zSets = new ConcurrentHashMap<>();
    final List<String> deletedKeys = new ArrayList<>();

    String publishedChannel;
    String publishedMessage;

    private final ValueOperations<String, String> valueOperations = proxy(ValueOperations.class, this::handleValueOperation);
    private final ZSetOperations<String, String> zSetOperations = proxy(ZSetOperations.class, this::handleZSetOperation);

    @Override
    public ValueOperations<String, String> opsForValue() {
        return valueOperations;
    }

    @Override
    public ZSetOperations<String, String> opsForZSet() {
        return zSetOperations;
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
