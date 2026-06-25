package com.ssafy.wswg.config;

import com.ssafy.wswg.security.CustomOAuth2UserService;
import com.ssafy.wswg.security.FrontendOriginResolver;
import com.ssafy.wswg.security.JwtAuthenticationFilter;
import com.ssafy.wswg.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final FrontendOriginResolver frontendOriginResolver;


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{
        http.csrf((csrf) -> csrf.disable());


        return http.authorizeHttpRequests((auth) -> auth
                .requestMatchers(
                        "/",
                        "/login",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/auth/refresh",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/ws/plans/**").permitAll()
                // 공개 조회: 관광지·지역 목록/상세는 비로그인 랜딩에서도 노출 (GET 만 허용)
                .requestMatchers(HttpMethod.GET,
                        "/api/attractions",
                        "/api/attractions/**",
                        "/api/sidos",
                        "/api/guguns",
                        "/api/content-types").permitAll()
                .requestMatchers("/admin").hasRole("ADMIN")
                .anyRequest().authenticated())

                .oauth2Login((oauth2)->oauth2
                        .userInfoEndpoint((userInfo)-> userInfo
                                .userService(customOAuth2UserService))
                                    .successHandler(oAuth2SuccessHandler)
                                    .failureHandler((request, response, exception) -> {
                                        exception.printStackTrace();
                                        response.sendRedirect(frontendOriginResolver.resolve(request) + "/?loginError=true");
                                    }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
