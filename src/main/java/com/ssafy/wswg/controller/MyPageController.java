package com.ssafy.wswg.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.GroupDto;
import com.ssafy.wswg.model.dto.MyPageTripRequest;
import com.ssafy.wswg.model.dto.MyPageTripResponse;
import com.ssafy.wswg.model.service.GroupService;
import com.ssafy.wswg.model.service.TripService;
import com.ssafy.wswg.security.LoginUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MyPageController {
    private final TripService tripService;
    private final GroupService groupService;

    @GetMapping("/trips")
    public ResponseEntity<List<MyPageTripResponse>> readTrips(
            @LoginUserId Long userId,
            @ModelAttribute MyPageTripRequest request) {
        return ResponseEntity.ok(tripService.readMyPageTrips(userId, request.getScope(), request.getStatus()));
    }

    @GetMapping("/groups")
    public ResponseEntity<List<GroupDto>> readGroups(@LoginUserId Long userId) {
        return ResponseEntity.ok(groupService.readGroups(userId));
    }
}
