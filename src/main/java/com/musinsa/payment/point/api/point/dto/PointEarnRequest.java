package com.musinsa.payment.point.api.point.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 포인트 적립 요청 DTO (Request DTO)
 * - Record 사용: 불변(Immutable) 객체로, 요청 데이터의 무결성을 보장함.
 * - Controller 진입 시 @Valid를 통해 유효성 검사가 필수적으로 수행됨.
 */
public record PointEarnRequest(
        @NotNull Long userId,
        @Min(1) long amount,
        boolean isManual, // 관리자 수기 지급 여부

        @NotBlank(message = "참조 ID(주문번호, 이벤트적립번호)는 필수입니다.")
        String refId // 중복 적립 방지를 위한 외부 식별자
) {}