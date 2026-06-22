package com.ssafy.wswg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.ssafy.wswg.controller.MyPageController;
import com.ssafy.wswg.model.dto.GroupDto;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.dto.TripStatus;
import com.ssafy.wswg.model.service.GroupService;
import com.ssafy.wswg.model.service.TripService;
import com.ssafy.wswg.security.LoginUserId;

class MyPageControllerTest {
    private static final Long LOGIN_USER_ID = 1L;

    private MockMvc mockMvc;
    private RecordingTripService tripService;
    private RecordingGroupService groupService;

    @BeforeEach
    void setUp() {
        tripService = new RecordingTripService();
        groupService = new RecordingGroupService();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new MyPageController(tripService, groupService))
                .setCustomArgumentResolvers(new TestLoginUserIdArgumentResolver())
                .build();
    }

    @Test
    void readGroups_returnsMyGroups() throws Exception {
        GroupDto group = new GroupDto();
        group.setGroupId(10L);
        group.setGroupName("제주 여행 모임");
        group.setOwnerId(LOGIN_USER_ID);
        group.setMemberCount(3);
        groupService.groups = List.of(group);

        mockMvc.perform(get("/api/mypage/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(10))
                .andExpect(jsonPath("$[0].groupName").value("제주 여행 모임"))
                .andExpect(jsonPath("$[0].memberCount").value(3));

        assertThat(groupService.userId).isEqualTo(LOGIN_USER_ID);
    }

    @Test
    void readTrips_passesScopeAndStatusToService() throws Exception {
        MyPageTripResponse trip = new MyPageTripResponse();
        trip.setTripId(20L);
        trip.setTitle("부산 여행");
        trip.setStatus(TripStatus.ONGOING);
        tripService.trips = List.of(trip);

        mockMvc.perform(get("/api/mypage/trips")
                        .param("scope", "joined")
                        .param("status", "ONGOING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tripId").value(20))
                .andExpect(jsonPath("$[0].title").value("부산 여행"))
                .andExpect(jsonPath("$[0].status").value("ONGOING"))
                .andExpect(jsonPath("$[0].statusLabel").value("여행중"));

        assertThat(tripService.userId).isEqualTo(LOGIN_USER_ID);
        assertThat(tripService.scope).isEqualTo("joined");
        assertThat(tripService.status).isEqualTo("ONGOING");
    }

    private static class RecordingTripService extends TripService {
        private Long userId;
        private String scope;
        private String status;
        private List<MyPageTripResponse> trips = List.of();

        RecordingTripService() {
            super(null);
        }

        @Override
        public List<MyPageTripResponse> readMyPageTrips(Long userId, String scope, String status) {
            this.userId = userId;
            this.scope = scope;
            this.status = status;
            return trips;
        }
    }

    private static class RecordingGroupService extends GroupService {
        private Long userId;
        private List<GroupDto> groups = List.of();

        RecordingGroupService() {
            super(null, null);
        }

        @Override
        public List<GroupDto> readGroups(Long userId) {
            this.userId = userId;
            return groups;
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
