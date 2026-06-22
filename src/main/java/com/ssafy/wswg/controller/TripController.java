package com.ssafy.wswg.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.TripCreateRequestDto;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.dto.TripUpdateRequestDto;
import com.ssafy.wswg.model.service.TripService;
import com.ssafy.wswg.security.LoginUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripDto> createTrip(
            @LoginUserId Long userId,
            @RequestBody(required = false) TripCreateRequestDto request) {
        TripDto trip = tripService.createTrip(userId, request);

        return ResponseEntity
                .created(URI.create("/api/trips/" + trip.getTripId()))
                .body(trip);
    }

    @GetMapping
    public ResponseEntity<List<TripDto>> readTrips(
            @LoginUserId Long userId,
            @RequestParam(required = false) Long groupId) {
        if (groupId == null) {
            return ResponseEntity.ok(tripService.readMyTrips(userId));
        }

        return ResponseEntity.ok(tripService.readGroupTrips(groupId, userId));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripDto> readTrip(
            @LoginUserId Long userId,
            @PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.readTrip(tripId, userId));
    }

    @PutMapping("/{tripId}")
    public ResponseEntity<TripDto> updateTrip(
            @LoginUserId Long userId,
            @PathVariable Long tripId,
            @RequestBody(required = false) TripUpdateRequestDto request) {
        return ResponseEntity.ok(tripService.updateTrip(tripId, userId, request));
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> deleteTrip(
            @LoginUserId Long userId,
            @PathVariable Long tripId) {
        tripService.deleteTrip(tripId, userId);
        return ResponseEntity.noContent().build();
    }
}
