package com.ssafy.wswg.security;

import com.ssafy.wswg.model.dto.TokenResponseDto;
import com.ssafy.wswg.model.dto.UserDto;
import com.ssafy.wswg.model.service.RefreshTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final RefreshTokenService refreshTokenService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        UserDto userDto = oAuth2User.getUserDto();

        TokenResponseDto tokenResponse = refreshTokenService.issueTokens(
                userDto.getId(),
                userDto.getEmail(),
                String.valueOf(userDto.getRole())
        );
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", tokenResponse.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/auth/refresh")
                .maxAge(Duration.ofMillis(refreshTokenService.getRefreshTokenMaxAgeMillis()))
                .sameSite("Lax")
                .build();

        response.addHeader("Set-Cookie", refreshTokenCookie.toString());

        //TODO:: 프론트엔드로 리다이렉트할 주소 만들기
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/login/success")
                .queryParam("token", tokenResponse.getAccessToken())
                .queryParam("accessToken", tokenResponse.getAccessToken())
                        .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
