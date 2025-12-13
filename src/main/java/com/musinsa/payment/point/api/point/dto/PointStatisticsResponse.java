package com.musinsa.payment.point.api.point.dto;

import com.musinsa.payment.point.domain.point.enums.PointType;
import lombok.*;

/**
 * 포인트 통계 응답 DTO (Response DTO)
 * - 목적: 관리자 대시보드에서 기간별 총 적립액/사용액 등을 보여주는 통계 지표.
 * - JPQL Select New 구문 사용 필수: DTO 생성자가 JPQL에 의해 직접 호출되므로 AllArgsConstructor가 필요함.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointStatisticsResponse {

    private PointType type;   // SAVE(적립), USE(사용) 등 상태
    private long totalAmount; // 해당 상태의 합계 금액

    // JPQL의 SUM() 결과가 Long으로 반환될 수 있으므로, 방어를 위해 Long 타입을 받는 생성자 추가
    public PointStatisticsResponse(PointType type, Long totalAmount) {
        this.type = type;
        this.totalAmount = totalAmount != null ? totalAmount : 0L;
    }
}