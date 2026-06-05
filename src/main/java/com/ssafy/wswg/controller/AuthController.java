package com.ssafy.wswg.controller;

import com.ssafy.wswg.model.dto.AccessTokenResponseDto;
import com.ssafy.wswg.model.dto.TokenResponseDto;
import com.ssafy.wswg.model.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponseDto> refresh(@CookieValue("refreshToken") String refreshToken) {
        TokenResponseDto tokenResponse = refreshTokenService.rotateRefreshToken(refreshToken);
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", tokenResponse.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/auth/refresh")
                .maxAge(Duration.ofMillis(refreshTokenService.getRefreshTokenMaxAgeMillis()))
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshTokenCookie.toString())
                .body(new AccessTokenResponseDto(tokenResponse.getAccessToken()));
    }
}
