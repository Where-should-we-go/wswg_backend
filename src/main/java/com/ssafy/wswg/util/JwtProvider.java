package com.ssafy.wswg.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtProvider {

    private final Key key;
    private final long expirationTime;
    private final long refreshExpirationTime;


    public JwtProvider(
            @Value("${jwt.secret}")String secretKey,
            @Value("${jwt.expiration}") long expirationTime,
            @Value("${jwt.refresh-expiration}") long refreshExpirationTime
    ) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
        this.expirationTime = expirationTime;
        this.refreshExpirationTime = refreshExpirationTime;
    }

    public String createAccessToken(Long userId, String email, String role) {
        return createToken(userId, email, role, "access", null, expirationTime);
    }

    public String createRefreshToken(Long userId, String email, String role, long version) {
        return createToken(userId, email, role, "refresh", version, refreshExpirationTime);
    }

    // 🔍 2. JWT 토큰이 유효한지 검증하는 메서드
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            System.out.println("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            System.out.println("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            System.out.println("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            System.out.println("JWT 토큰이 비어있거나 잘못되었습니다.");
        }
        return false;
    }

    // 🧾 3. JWT 토큰에서 유저 ID(PK) 꺼내는 메서드
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);

        return Long.parseLong(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    public String getTokenTypeFromToken(String token) {
        return getClaims(token).get("type", String.class);
    }

    public Long getVersionFromToken(String token) {
        return getClaims(token).get("version", Long.class);
    }

    public long getRefreshExpirationTime() {
        return refreshExpirationTime;
    }

    private String createToken(Long userId, String email, String role, String type, Long version, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        JwtBuilder builder = Jwts.builder()
                .setSubject(String.valueOf(userId)) // 토큰 주인 (보통 유저 PK ID)
                .claim("email", email)             // 커스텀 데이터 (이메일)
                .claim("role", role)               // 커스텀 데이터 (권한)
                .claim("type", type)               // access / refresh 구분
                .setIssuedAt(now)                  // 토큰 발행 시간
                .setExpiration(expiryDate)         // 토큰 만료 시간
                .signWith(key, SignatureAlgorithm.HS256); // 암호화 알고리즘과 비밀키

        if (version != null) {
            builder.claim("version", version);
        }

        return builder.compact();
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
