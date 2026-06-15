package com.ssafy.wswg.external.tour;

import lombok.Getter;

/**
 * TourAPI 호출 실패. resultCode 또는 전송 계층 오류를 사람이 다룰 수 있는
 * {@link TourApiErrorType}로 분류하고, 재시도 가능 여부를 함께 제공한다.
 */
@Getter
public class TourApiException extends RuntimeException {

    /**
     * 실패 분류.
     * <ul>
     *   <li>{@code QUOTA}    - 쿼터 초과(resultCode 22). 재시도 불가.</li>
     *   <li>{@code KEY}      - 서비스키 오류(resultCode 30/31). 재시도 불가.</li>
     *   <li>{@code TRANSIENT}- 일시적 오류(resultCode 01/02/04/05, 5xx, IO/timeout). 재시도 가능.</li>
     *   <li>{@code UNKNOWN}  - 분류 불가. 재시도 불가.</li>
     * </ul>
     */
    public enum TourApiErrorType {
        QUOTA, KEY, TRANSIENT, UNKNOWN
    }

    /** 재시도 가능한 오류인지 여부. */
    private final boolean retryable;

    /** 분류된 오류 유형. */
    private final TourApiErrorType errorType;

    /** 원본 resultCode(전송 계층 오류면 null일 수 있음). */
    private final String resultCode;

    public TourApiException(TourApiErrorType errorType, boolean retryable, String resultCode, String message) {
        super(message);
        this.errorType = errorType;
        this.retryable = retryable;
        this.resultCode = resultCode;
    }

    public TourApiException(TourApiErrorType errorType, boolean retryable, String resultCode, String message,
            Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.retryable = retryable;
        this.resultCode = resultCode;
    }

    /** resultCode를 유형으로 매핑해 예외를 생성한다. */
    public static TourApiException fromResultCode(String resultCode, String resultMsg) {
        String msg = "TourAPI resultCode=" + resultCode + ", resultMsg=" + resultMsg;
        return switch (resultCode == null ? "" : resultCode) {
            case "22" -> new TourApiException(TourApiErrorType.QUOTA, false, resultCode, msg);
            case "30", "31" -> new TourApiException(TourApiErrorType.KEY, false, resultCode, msg);
            // 01=APPLICATION, 02=DB, 04=HTTP, 05=SERVICETIMEOUT — 모두 일시적 → 재시도.
            case "01", "02", "04", "05" -> new TourApiException(TourApiErrorType.TRANSIENT, true, resultCode, msg);
            default -> new TourApiException(TourApiErrorType.UNKNOWN, false, resultCode, msg);
        };
    }
}
