package com.ssafy.wswg.model.service;

import com.ssafy.wswg.model.dto.TokenResponseDto;
import com.ssafy.wswg.util.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String TOKEN_HASH_FIELD = "tokenHash";
    private static final String VERSION_FIELD = "version";

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate stringRedisTemplate;

    public TokenResponseDto issueTokens(Long userId, String email, String role) {
        long version = getCurrentVersion(userId) + 1;
        String accessToken = jwtProvider.createAccessToken(userId, email, role);
        String refreshToken = jwtProvider.createRefreshToken(userId, email, role, version);

        saveRefreshToken(userId, refreshToken, version);

        return new TokenResponseDto(accessToken, refreshToken);
    }

    public TokenResponseDto rotateRefreshToken(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken) || !"refresh".equals(jwtProvider.getTokenTypeFromToken(refreshToken))) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.");
        }

        Long userId = jwtProvider.getUserIdFromToken(refreshToken);
        String email = jwtProvider.getEmailFromToken(refreshToken);
        String role = jwtProvider.getRoleFromToken(refreshToken);
        Long tokenVersion = jwtProvider.getVersionFromToken(refreshToken);

        String key = getRefreshKey(userId);
        String savedTokenHash = (String) stringRedisTemplate.opsForHash().get(key, TOKEN_HASH_FIELD);
        String savedVersion = (String) stringRedisTemplate.opsForHash().get(key, VERSION_FIELD);

        if (savedTokenHash == null || savedVersion == null) {
            throw new IllegalArgumentException("저장된 리프레시 토큰이 없습니다.");
        }

        if (!savedTokenHash.equals(hash(refreshToken)) || !savedVersion.equals(String.valueOf(tokenVersion))) {
            deleteRefreshToken(userId);
            throw new IllegalArgumentException("이미 사용됐거나 탈취 의심 리프레시 토큰입니다.");
        }

        return issueTokens(userId, email, role);
    }

    public void deleteRefreshToken(Long userId) {
        stringRedisTemplate.delete(getRefreshKey(userId));
    }

    public long getRefreshTokenMaxAgeMillis() {
        return jwtProvider.getRefreshExpirationTime();
    }

    private void saveRefreshToken(Long userId, String refreshToken, long version) {
        String key = getRefreshKey(userId);

        stringRedisTemplate.opsForHash().put(key, TOKEN_HASH_FIELD, hash(refreshToken));
        stringRedisTemplate.opsForHash().put(key, VERSION_FIELD, String.valueOf(version));
        stringRedisTemplate.expire(key, Duration.ofMillis(jwtProvider.getRefreshExpirationTime()));
    }

    private long getCurrentVersion(Long userId) {
        String version = (String) stringRedisTemplate.opsForHash().get(getRefreshKey(userId), VERSION_FIELD);

        if (version == null) {
            return 0L;
        }

        return Long.parseLong(version);
    }

    private String getRefreshKey(Long userId) {
        return REFRESH_KEY_PREFIX + userId;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
