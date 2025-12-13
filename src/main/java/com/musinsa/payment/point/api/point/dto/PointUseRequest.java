package com.musinsa.payment.point.api.point.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 포인트 사용 요청 DTO (Request DTO)
 * - Record 사용: 불변성을 보장하며, 사용 트랜잭션의 입력 데이터 역할을 함.
 */
public record PointUseRequest(
        @NotNull Long userId,
        @Min(value = 1, message = "사용 금액은 1원 이상이어야 합니다.")
        long amount,

        @NotBlank(message = "주문 ID는 필수입니다.")
        String orderId // 거래 추적 및 멱등성 검증에 사용될 외부 시스템 ID (예: 주문번호)
) {}