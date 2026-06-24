package com.ssafy.wswg.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * OAuth 로그인 성공/실패 후 리다이렉트할 프론트엔드 origin 을 결정한다.
 *
 * <p>요청이 들어온 호스트(scheme + Host 헤더)를 origin 으로 보고, 허용 목록(allowed-origins)에
 * 포함될 때만 그 값을 사용한다. 이렇게 하면 호스트 PC(localhost)와 같은 LAN 의 다른 PC(LAN IP)가
 * 같은 백엔드를 써도 각자 들어온 주소로 정확히 되돌아간다.
 *
 * <p>허용 목록에 없으면 accessToken 이 임의 호스트로 새는 open-redirect 를 막기 위해
 * 목록의 첫 항목(기본 origin)으로 폴백한다. vite dev 프록시는 기본적으로 원본 Host 헤더를
 * 보존(changeOrigin=false)하므로 브라우저가 접속한 주소가 그대로 백엔드까지 전달된다.
 */
@Component
public class FrontendOriginResolver {

    private final List<String> allowedOrigins;

    public FrontendOriginResolver(
            @Value("${app.frontend.allowed-origins:http://localhost:3000}") String allowedOriginsCsv) {
        this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    /** 기본 origin(허용 목록 첫 항목). 목록이 비면 localhost:3000. */
    public String defaultOrigin() {
        return allowedOrigins.isEmpty() ? "http://localhost:3000" : allowedOrigins.get(0);
    }

    /** 요청 호스트가 허용 목록에 있으면 그 origin, 아니면 기본 origin. */
    public String resolve(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return defaultOrigin();
        }
        String origin = request.getScheme() + "://" + host;
        return allowedOrigins.contains(origin) ? origin : defaultOrigin();
    }
}
