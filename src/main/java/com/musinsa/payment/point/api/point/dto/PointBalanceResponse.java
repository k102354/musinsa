package com.musinsa.payment.point.api.point.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 포인트 잔액 응답 DTO (Response DTO)
 * - UserPointWallet 테이블에서 조회한 사용자의 현재 총 잔액을 전달.
 */
@Getter
@Builder
public class PointBalanceResponse {

    private Long userId;        // 사용자 ID
    private long currentBalance; // 현재 보유 잔액 (UserPointWallet.balance)

    /**
     * 팩토리 메서드: 잔액 조회 결과를 DTO로 변환
     */
    public static PointBalanceResponse of(Long userId, long amount) {
        return PointBalanceResponse.builder()
                .userId(userId)
                .currentBalance(amount)
                .build();
    }
}