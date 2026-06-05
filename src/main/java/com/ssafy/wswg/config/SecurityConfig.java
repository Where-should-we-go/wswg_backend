package com.ssafy.wswg.config;

import com.ssafy.wswg.security.CustomOAuth2UserService;
import com.ssafy.wswg.security.JwtAuthenticationFilter;
import com.ssafy.wswg.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{
        http.csrf((csrf) -> csrf.disable());


        return http.authorizeHttpRequests((auth) -> auth
                .requestMatchers("/", "/login", "/oauth2/**", "/login/oauth2/**", "/auth/refresh").permitAll()
                .requestMatchers("/admin").hasRole("ADMIN")
                .anyRequest().authenticated())

                .oauth2Login((oauth2)->oauth2
                        .userInfoEndpoint((userInfo)-> userInfo
                                .userService(customOAuth2UserService))
                                    .successHandler(oAuth2SuccessHandler)
                                    .failureHandler((request, response, exception) -> {
                                        exception.printStackTrace();
                                        response.sendRedirect("http://localhost:3000/?loginError=true");
                                    }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
