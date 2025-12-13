package com.musinsa.payment.point.api.point.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PointCancelUseRequest(
        @NotNull Long userId,
        @NotBlank String orderId, // 취소할 주문번호
        @Min(1) long cancelAmount // 취소 금액
) {}