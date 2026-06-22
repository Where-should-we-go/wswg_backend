package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.controller.TripController;
import com.ssafy.wswg.model.dto.TripCreateRequestDto;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.dto.TripUpdateRequestDto;
import com.ssafy.wswg.model.service.TripService;
import com.ssafy.wswg.security.LoginUserId;

class TripControllerTest {
    private static final Long LOGIN_USER_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;
    private RecordingTripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new RecordingTripService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TripController(tripService))
                .setCustomArgumentResolvers(new TestLoginUserIdArgumentResolver())
                .build();
    }

    @Test
    void createTrip_returns201WithLocation() throws Exception {
        TripCreateRequestDto request = new TripCreateRequestDto();
        request.setTitle("제주 여행");
        request.setStartDate(LocalDate.of(2026, 7, 1));
        request.setEndDate(LocalDate.of(2026, 7, 3));

        mockMvc.perform(post("/api/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/trips/10"))
                .andExpect(jsonPath("$.tripId").value(10))
                .andExpect(jsonPath("$.title").value("제주 여행"));

        assertThat(tripService.createUserId).isEqualTo(LOGIN_USER_ID);
    }

    @Test
    void readTrips_withGroupIdDelegatesGroupList() throws Exception {
        mockMvc.perform(get("/api/trips").param("groupId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(7));

        assertThat(tripService.readGroupId).isEqualTo(7L);
    }

    @Test
    void updateAndDeleteTrip_delegateToService() throws Exception {
        TripUpdateRequestDto request = new TripUpdateRequestDto();
        request.setTitle("수정된 여행");

        mockMvc.perform(put("/api/trips/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 여행"));

        mockMvc.perform(delete("/api/trips/10"))
                .andExpect(status().isNoContent());

        assertThat(tripService.updateTripId).isEqualTo(10L);
        assertThat(tripService.deleteTripId).isEqualTo(10L);
    }

    private static class RecordingTripService extends TripService {
        private Long createUserId;
        private Long readGroupId;
        private Long updateTripId;
        private Long deleteTripId;

        RecordingTripService() {
            super(null, null);
        }

        @Override
        public TripDto createTrip(Long userId, TripCreateRequestDto request) {
            createUserId = userId;
            TripDto trip = new TripDto();
            trip.setTripId(10L);
            trip.setTitle(request.getTitle());
            trip.setStartDate(request.getStartDate());
            trip.setEndDate(request.getEndDate());
            trip.setUserId(userId);
            return trip;
        }

        @Override
        public List<TripDto> readMyTrips(Long userId) {
            TripDto trip = new TripDto();
            trip.setTripId(10L);
            trip.setUserId(userId);
            return List.of(trip);
        }

        @Override
        public List<TripDto> readGroupTrips(Long groupId, Long userId) {
            readGroupId = groupId;
            TripDto trip = new TripDto();
            trip.setTripId(11L);
            trip.setGroupId(groupId);
            return List.of(trip);
        }

        @Override
        public TripDto readTrip(Long tripId, Long userId) {
            TripDto trip = new TripDto();
            trip.setTripId(tripId);
            trip.setUserId(userId);
            return trip;
        }

        @Override
        public TripDto updateTrip(Long tripId, Long userId, TripUpdateRequestDto request) {
            updateTripId = tripId;
            TripDto trip = new TripDto();
            trip.setTripId(tripId);
            trip.setTitle(request.getTitle());
            return trip;
        }

        @Override
        public void deleteTrip(Long tripId, Long userId) {
            deleteTripId = tripId;
        }
    }

    private static class TestLoginUserIdArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(LoginUserId.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return LOGIN_USER_ID;
        }
    }
}
