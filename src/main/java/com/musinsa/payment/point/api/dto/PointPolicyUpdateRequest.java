package com.musinsa.payment.point.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PointPolicyUpdateRequest(
        @Min(value = 1, message = "최대 적립액은 1원 이상이어야 합니다.")
        @Max(value = 100_000, message = "1회 최대 적립액은 100,000원을 넘을 수 없습니다.")
        Long maxEarnAmount,

        @Min(value = 1, message = "보유 한도는 1원 이상이어야 합니다.")
        Long maxPossessionLimit,

        @Min(value = 1, message = "만료 기간은 최소 1일 이상이어야 합니다.")
        @Max(value = 1824, message = "만료 기간은 5년 미만이어야 합니다.")
        Integer defaultExpireDays
) {
    // ★ 이 메서드가 'true'여야만 유효성 검사를 통과합니다.
    @JsonIgnore // Swagger나 JSON 응답에 포함되지 않도록 숨김
    @AssertTrue(message = "변경할 정책 파라미터가 최소 하나는 존재해야 합니다.")
    public boolean isAtLeastOneFieldPresent() {
        return maxEarnAmount != null || maxPossessionLimit != null || defaultExpireDays != null;
    }
}