package com.ssafy.wswg;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import com.ssafy.wswg.controller.AttractionController;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dto.AttractionDetailDto;
import com.ssafy.wswg.model.dto.AttractionSearchCondition;
import com.ssafy.wswg.model.dto.AttractionSummaryDto;
import com.ssafy.wswg.model.dto.PagedResponse;
import com.ssafy.wswg.model.service.AttractionDetailService;
import com.ssafy.wswg.model.service.AttractionSearchService;
import com.ssafy.wswg.model.service.AttractionService;
import com.ssafy.wswg.security.JwtAuthenticationFilter;

/**
 * AttractionController 슬라이스 테스트. 보안 처리는 다른 컨트롤러 테스트와 동일.
 * RestApiExceptionHandler(@RestControllerAdvice)는 슬라이스에 자동 포함되어 400 변환을 검증한다.
 */
@WebMvcTest(controllers = AttractionController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class},
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, WebMvcConfig.class,
                        JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class AttractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttractionSearchService attractionSearchService;

    @MockBean
    private AttractionService attractionService;

    @MockBean
    private AttractionDetailService attractionDetailService;

    @Test
    void search_returns200WithPagedJson() throws Exception {
        AttractionSummaryDto a = new AttractionSummaryDto(126508, "경복궁", "서울", "종로구", 12, "https://img");
        given(attractionSearchService.search(any(), anyInt(), anyInt()))
                .willReturn(new PagedResponse<>(List.of(a), 0, 12, 1));

        mockMvc.perform(get("/api/attractions").param("sidoCode", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contentId").value(126508))
                .andExpect(jsonPath("$.content[0].sidoName").value("서울"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void search_emptyResult_returns200WithEmptyContent() throws Exception {
        given(attractionSearchService.search(any(), anyInt(), anyInt()))
                .willReturn(new PagedResponse<>(List.of(), 0, 12, 0));

        mockMvc.perform(get("/api/attractions").param("keyword", "없는장소"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void search_negativePage_returns400() throws Exception {
        given(attractionSearchService.search(any(), anyInt(), anyInt()))
                .willThrow(new CommonException(ErrorCode.INVALID_PAGINATION));

        mockMvc.perform(get("/api/attractions").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40006));
    }

    @Test
    void search_multipleContentTypeId_bindsToList() throws Exception {
        given(attractionSearchService.search(any(), anyInt(), anyInt()))
                .willReturn(new PagedResponse<>(List.of(), 0, 12, 0));

        mockMvc.perform(get("/api/attractions")
                        .param("contentTypeId", "12")
                        .param("contentTypeId", "14"))
                .andExpect(status().isOk());

        ArgumentCaptor<AttractionSearchCondition> captor =
                ArgumentCaptor.forClass(AttractionSearchCondition.class);
        verify(attractionSearchService).search(captor.capture(), anyInt(), anyInt());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getContentTypeIds())
                .containsExactly(12, 14);
    }

    @Test
    void getDetail_returns200WithJson() throws Exception {
        AttractionDetailDto d = new AttractionDetailDto();
        d.setContentId(126508);
        d.setTitle("경복궁");
        d.setSidoName("서울");
        d.setContentTypeName("관광지");
        d.setOverview("조선의 법궁");
        d.setRestDate("매주 화요일");
        given(attractionDetailService.getDetail(126508)).willReturn(d);

        mockMvc.perform(get("/api/attractions/126508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentId").value(126508))
                .andExpect(jsonPath("$.contentTypeName").value("관광지"))
                .andExpect(jsonPath("$.overview").value("조선의 법궁"))
                .andExpect(jsonPath("$.restDate").value("매주 화요일"));
    }

    @Test
    void getDetail_notFound_returns404() throws Exception {
        given(attractionDetailService.getDetail(999))
                .willThrow(new CommonException(ErrorCode.NOT_FOUND_ATTRACTION));

        mockMvc.perform(get("/api/attractions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40402));
    }
}
