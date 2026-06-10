package com.ssafy.wswg.controller;

import com.ssafy.wswg.model.dto.AccessTokenResponseDto;
import com.ssafy.wswg.model.dto.TokenResponseDto;
import com.ssafy.wswg.model.dto.UserDto;
import com.ssafy.wswg.model.dao.UserDao;
import com.ssafy.wswg.model.service.RefreshTokenService;
import com.ssafy.wswg.security.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final UserDao userDao;

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        UserDto user = userDao.findById(userId);

        return ResponseEntity.ok(user);
    }

    private Long resolveUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof Long userId) {
            return userId;
        }

        if (principal instanceof CustomOAuth2User oAuth2User) {
            return oAuth2User.getUserDto().getId();
        }

        throw new IllegalStateException("지원하지 않는 인증 정보입니다.");
    }

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

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        refreshTokenService.deleteRefreshToken(userId);

        ResponseCookie expiredRefreshTokenCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/auth/refresh")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        return ResponseEntity.noContent()
                .header("Set-Cookie", expiredRefreshTokenCookie.toString())
                .build();
    }
}
