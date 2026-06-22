package com.ssafy.wswg.model.dto;

public enum TripStatus {
    UPCOMING("예정"),
    ONGOING("여행중"),
    COMPLETED("완료");

    private final String label;

    TripStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
