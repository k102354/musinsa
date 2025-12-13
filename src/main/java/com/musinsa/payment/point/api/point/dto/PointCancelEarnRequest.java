package com.musinsa.payment.point.api.point.dto;

import jakarta.validation.constraints.NotNull;

public record PointCancelEarnRequest(
        @NotNull(message = "유저 ID는 필수입니다.")
        Long userId,

        @NotNull(message = "취소할 적립 번호는 필수입니다.")
        long pointItemId,

        boolean isManual // 기본값: false
) {
    public PointCancelEarnRequest(Long userId, long pointItemId) {
        this(userId, pointItemId, false); // isManual에 false 강제 주입
    }
}