package com.musinsa.payment.point.api.point.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 소멸 예정 포인트 응답 DTO (Response DTO)
 * - 사용자에게 유효기간 만료가 임박한 포인트 목록을 알림.
 * - 응답에는 PointItem의 잔액, 만료일, 그리고 추적을 위한 Item ID가 포함됨.
 */
@Getter
@Builder
public class PointExpiringResponse {
    private long amount; // 소멸 예정 금액 (remainAmount)
    private LocalDateTime expireDate; // 소멸 일시 (expireAt)
    private long pointItemId; // 해당 포인트 원장(PointItem)의 ID (추적용)
}