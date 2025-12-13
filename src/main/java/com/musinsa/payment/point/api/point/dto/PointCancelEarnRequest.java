package com.musinsa.payment.point.api.point.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 포인트 적립 취소 요청 DTO (Request DTO)
 * - 사용자에게 지급된 특정 PointItem(원장) 전체를 취소(CANCELED 상태로 변경)함.
 * - 주로 시스템 오류나 CS 대응 시 관리자에 의해 사용됨.
 */
public record PointCancelEarnRequest(
        @NotNull(message = "유저 ID는 필수입니다.")
        Long userId,
        @NotNull(message = "취소할 적립 번호(PointItem ID)는 필수입니다.")
        long pointItemId, // 적립 취소할 대상 PointItem의 ID
        boolean isManual // 관리자 수기 취소 여부 (로깅 및 권한 체크용)
) {
    public PointCancelEarnRequest(Long userId, long pointItemId) {
        // isManual 기본값 false 강제 주입: 시스템적인 취소가 기본값임을 명시
        this(userId, pointItemId, false);
    }
}