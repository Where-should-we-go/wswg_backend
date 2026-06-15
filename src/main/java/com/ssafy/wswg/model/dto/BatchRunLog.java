package com.ssafy.wswg.model.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * batch_run_log 한 행. A-3 TourAPI 적재가 매 실행마다 정확히 1행을 남긴다
 * (SUCCESS / DEGRADED / ABORTED).
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRunLog {
    private Long id;
    private String jobName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer totalCount;
    private Integer attractionCount;
    private Integer sidoCount;
    private Integer gugunCount;
    private Integer skippedValidation;
    private Integer skippedFk;
    private String errorCode;
    private String errorMessage;
}
