package com.ssafy.wswg;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.wswg.config.SecurityConfig;
import com.ssafy.wswg.config.WebMvcConfig;
import com.ssafy.wswg.controller.RegionController;
import com.ssafy.wswg.model.dto.ContentTypeDto;
import com.ssafy.wswg.model.dto.GugunDto;
import com.ssafy.wswg.model.dto.SidoDto;
import com.ssafy.wswg.model.service.RegionService;
import com.ssafy.wswg.security.JwtAuthenticationFilter;

/**
 * RegionController 슬라이스 테스트.
 * 보안 처리는 {@link AdminTourControllerTest}와 동일(필터 비활성 + 시큐리티 오토컨피그 제외).
 */
@WebMvcTest(controllers = RegionController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class},
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, WebMvcConfig.class,
                        JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class RegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegionService regionService;

    @Test
    void getSidos_returns200WithList() throws Exception {
        given(regionService.getSidos()).willReturn(List.of(
                new SidoDto(11, "서울"),
                new SidoDto(26, "부산")));

        mockMvc.perform(get("/api/sidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sidoCode").value(11))
                .andExpect(jsonPath("$[0].sidoName").value("서울"));
    }

    @Test
    void getGuguns_withSidoCode_returns200WithList() throws Exception {
        given(regionService.getGuguns(11)).willReturn(List.of(
                new GugunDto(11, 110, "종로구")));

        mockMvc.perform(get("/api/guguns").param("sidoCode", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gugunCode").value(110))
                .andExpect(jsonPath("$[0].gugunName").value("종로구"));
    }

    @Test
    void getContentTypes_returns200WithList() throws Exception {
        given(regionService.getContentTypes()).willReturn(List.of(
                new ContentTypeDto(12, "관광지"),
                new ContentTypeDto(14, "문화시설")));

        mockMvc.perform(get("/api/content-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contentTypeId").value(12))
                .andExpect(jsonPath("$[1].contentTypeName").value("문화시설"));
    }
}
