package com.ssafy.wswg.security;

import com.ssafy.wswg.model.dto.UserDto;
import com.ssafy.wswg.util.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtProvider jwtProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        UserDto userDto = oAuth2User.getUserDto();

        String token = jwtProvider.createAccessToken(userDto.getId(), userDto.getEmail(), String.valueOf(userDto.getRole()));

        //TODO:: 프론트엔드로 리다이렉트할 주소 만들기
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/login/success")
                .queryParam("token", token)
                        .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
