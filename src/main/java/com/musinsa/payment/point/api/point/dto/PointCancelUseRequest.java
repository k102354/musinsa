package com.musinsa.payment.point.api.point.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 포인트 사용 취소 요청 DTO (Request DTO)
 * - 원본 주문을 식별하고, 취소할 금액을 명확히 하는 것이 목적임.
 */
public record PointCancelUseRequest(
        @NotNull Long userId,

        @NotBlank(message = "취소할 원본 주문 ID는 필수입니다.")
        String orderId, // 원본 USE 거래를 찾기 위한 주문번호

        @Min(value = 1, message = "취소 금액은 1원 이상이어야 합니다.")
        long cancelAmount // 복구될 금액
) {}