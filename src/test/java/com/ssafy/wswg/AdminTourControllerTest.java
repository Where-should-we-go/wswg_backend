package com.ssafy.wswg;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;

import com.ssafy.wswg.config.SecurityConfig;
import com.ssafy.wswg.config.WebMvcConfig;
import com.ssafy.wswg.controller.AdminTourController;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.service.TourLoadService;
import com.ssafy.wswg.model.service.TourLoadService.Status;
import com.ssafy.wswg.model.service.TourLoadService.TourLoadResult;
import com.ssafy.wswg.security.JwtAuthenticationFilter;

/**
 * AdminTourController 슬라이스 테스트.
 *
 * <p>보안 처리: {@code @AutoConfigureMockMvc(addFilters = false)}로 시큐리티 필터 체인을
 * 비활성화해 인증 없이 엔드포인트에 도달하게 한다. 또한 시큐리티 관련 오토컨피규레이션을
 * 제외하고({@code excludeAutoConfiguration}), 슬라이스에 없는 빈(JwtProvider 등)에 의존하는
 * {@link SecurityConfig}/{@link WebMvcConfig}/{@link JwtAuthenticationFilter}를 컴포넌트 스캔
 * 제외 필터로 빼서 컨텍스트 로딩 실패를 막는다.
 * {@code RestApiExceptionHandler}(@RestControllerAdvice)는 슬라이스에 자동 포함된다.
 */
@WebMvcTest(controllers = AdminTourController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class},
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, WebMvcConfig.class,
                        JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class AdminTourControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TourLoadService tourLoadService;

    @Test
    void postLoad_success_returns200WithResult() throws Exception {
        TourLoadResult result = new TourLoadResult(Status.SUCCESS,
                LocalDateTime.of(2026, 6, 15, 4, 0),
                LocalDateTime.of(2026, 6, 15, 4, 5),
                100, 95, 17, 250, 3, 2, null, null);
        given(tourLoadService.load()).willReturn(result);

        mockMvc.perform(post("/admin/tour/load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalCount").value(100))
                .andExpect(jsonPath("$.attractionCount").value(95))
                .andExpect(jsonPath("$.sidoCount").value(17))
                .andExpect(jsonPath("$.gugunCount").value(250));
    }

    @Test
    void postLoad_whenAlreadyRunning_returns409() throws Exception {
        given(tourLoadService.load())
                .willThrow(new CommonException(ErrorCode.TOUR_LOAD_ALREADY_RUNNING));

        mockMvc.perform(post("/admin/tour/load"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getStatus_returnsRunningAndLast() throws Exception {
        TourLoadResult last = new TourLoadResult(Status.SUCCESS,
                LocalDateTime.of(2026, 6, 15, 4, 0),
                LocalDateTime.of(2026, 6, 15, 4, 5),
                100, 95, 17, 250, 0, 0, null, null);
        given(tourLoadService.isRunning()).willReturn(true);
        given(tourLoadService.getLast()).willReturn(last);

        mockMvc.perform(get("/admin/tour/load/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.last.status").value("SUCCESS"))
                .andExpect(jsonPath("$.last.attractionCount").value(95));
    }
}
