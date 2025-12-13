package com.musinsa.payment.point.api.policy.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 포인트 정책 업데이트 요청 DTO (Record)
 * - Record 사용: 불변성을 보장함.
 * - 목적: 최대 적립액, 보유 한도, 만료 기간 중 필요한 항목만 부분 업데이트를 요청함.
 */
public record PointPolicyUpdateRequest(
        @Min(value = 1, message = "최대 적립액은 1원 이상이어야 합니다.")
        @Max(value = 100_000, message = "1회 최대 적립액은 100,000원을 넘을 수 없습니다.")
        Long maxEarnAmount,

        @Min(value = 1, message = "최대 소유 한도는 1원 이상이어야 합니다.")
        Long maxPossessionLimit,

        @Min(value = 1, message = "만료 기간은 최소 1일 이상이어야 합니다.")
        @Max(value = 1824, message = "만료 기간은 5년 미만이어야 합니다.")
        Integer defaultExpireDays
) {

    /**
     * 유효성 검증: 변경할 정책 파라미터가 최소 하나는 존재하는지 체크
     * - @AssertTrue: DTO 유효성 검사 시 이 메서드가 호출되어 true를 반환해야 통과함.
     * - @JsonIgnore: API 문서나 응답 데이터에 포함되지 않도록 숨김.
     */
    @JsonIgnore // Swagger나 JSON 응답에 포함되지 않도록 숨김
    @AssertTrue(message = "변경할 정책 파라미터가 최소 하나는 존재해야 합니다.")
    public boolean isAtLeastOneFieldPresent() {
        return maxEarnAmount != null || maxPossessionLimit != null || defaultExpireDays != null;
    }
}