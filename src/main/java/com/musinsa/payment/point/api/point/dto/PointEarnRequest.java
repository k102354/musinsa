package com.musinsa.payment.point.api.point.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PointEarnRequest(
        @NotNull Long userId,
        @Min(1) long amount,
        boolean isManual // 관리자 수기 지급 여부
) {}