package com.musinsa.payment.point.api.point.dto;

import com.musinsa.payment.point.domain.point.entity.PointHistory;
import com.musinsa.payment.point.domain.point.enums.PointType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 포인트 거래 내역 응답 DTO (Response DTO)
 * - 마이페이지나 관리자 페이지에서 사용자의 포인트 거래 이력을 보여주기 위함.
 * - Class + @Builder 사용: 엔티티 매핑 시 유연하고 가독성 높은 방식으로 DTO 생성 가능.
 */
@Getter
@Builder
public class PointHistoryResponse {

    private Long pointHistoryId;
    private Long userId;
    private PointType type;      // SAVE, USE, USE_CANCEL 등 거래 유형
    private long amount;         // 거래 금액
    private String refId;        // 주문 번호 (거래 추적용)
    private LocalDateTime createAt; // 거래 발생 일시

    /**
     * 엔티티(PointHistory) -> DTO(PointHistoryResponse) 변환 메서드 (Static Factory Method)
     * @param entity PointHistory 엔티티
     * @return PointHistoryResponse
     */
    public static PointHistoryResponse from(PointHistory entity) {
        return PointHistoryResponse.builder()
                .pointHistoryId(entity.getId())
                .userId(entity.getUserId())
                .type(entity.getType())
                .amount(entity.getAmount())
                .refId(entity.getRefId())
                .createAt(entity.getCreatedAt())
                .build();
    }
}