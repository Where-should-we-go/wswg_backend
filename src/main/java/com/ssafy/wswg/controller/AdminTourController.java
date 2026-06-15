package com.ssafy.wswg.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.wswg.model.dto.ErrorResponseDto;
import com.ssafy.wswg.model.service.TourLoadService;
import com.ssafy.wswg.model.service.TourLoadService.TourLoadResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * A-3 TourAPI 적재 수동 트리거 + 상태 조회용 관리자 엔드포인트.
 *
 * <p>적재 자체는 {@link TourLoadService#load()}가 동기로 수행하며, 이미 진행 중이면
 * {@code CommonException(TOUR_LOAD_ALREADY_RUNNING)}을 던져 전역 핸들러가 409로 응답한다.
 * 컨트롤러는 예외를 잡지 않는다(전역 핸들러에 위임).
 *
 * <p>관리자 인증 가드는 프로젝트 계획상 후속 작업으로 미뤄져 있다(여기서 구현하지 않음).
 */
@Tag(name = "Admin - Tour Load", description = "TourAPI 적재 수동 트리거/상태")
@RestController
@RequestMapping("/admin/tour")
@RequiredArgsConstructor
public class AdminTourController {

    private final TourLoadService tourLoadService;

    /** 적재 상태 응답: 실행 중 여부 + 마지막 적재 결과(없으면 null). */
    public record StatusResponse(boolean running, TourLoadResult last) {
    }

    @Operation(summary = "TourAPI 적재 수동 실행(동기)",
            description = "지역+관광지를 동기로 적재한다. 이미 진행 중이면 409, 쿼터/키 오류·적재 실패 시 502.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "적재 완료(SUCCESS/DEGRADED 요약 반환)"),
            @ApiResponse(responseCode = "409", description = "이미 적재가 진행 중",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "502", description = "TourAPI 쿼터/키 오류 또는 적재 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/load")
    public ResponseEntity<TourLoadResult> load() {
        return ResponseEntity.ok(tourLoadService.load());
    }

    @Operation(summary = "TourAPI 적재 상태 조회",
            description = "현재 실행 중 여부와 마지막 적재 결과를 반환한다.")
    @ApiResponse(responseCode = "200", description = "실행 중 여부 + 마지막 적재 결과")
    @GetMapping("/load/status")
    public ResponseEntity<StatusResponse> status() {
        return ResponseEntity.ok(new StatusResponse(tourLoadService.isRunning(), tourLoadService.getLast()));
    }
}
