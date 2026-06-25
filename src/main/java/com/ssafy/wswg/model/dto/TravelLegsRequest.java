package com.ssafy.wswg.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 연속한 좌표쌍들의 이동 거리/시간을 한 번에 계산하기 위한 요청. */
@Getter
@Setter
@NoArgsConstructor
public class TravelLegsRequest {
    private TravelMode travelMode;
    private List<Leg> legs;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Leg {
        private Double fromLat;
        private Double fromLng;
        private Double toLat;
        private Double toLng;
    }
}
