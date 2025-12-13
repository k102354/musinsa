package com.musinsa.payment.point.api.point.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PointUseRequest(
        @NotNull Long userId,
        @Min(1) long amount,
        @NotBlank String orderId
) {}