package com.musinsa.payment.point.application.policy.service;

import com.musinsa.payment.point.api.policy.dto.PointPolicyUpdateRequest;
import com.musinsa.payment.point.domain.policy.entity.PointPolicy;
import com.musinsa.payment.point.domain.policy.repository.PointPolicyRepository;
import com.musinsa.payment.point.global.error.BusinessException;
import com.musinsa.payment.point.global.error.ErrorCode;
import com.musinsa.payment.point.global.policy.PointPolicyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 정책 관리 서비스
 * - 역할: PointPolicy 엔티티를 관리하고, 서비스 전반에 사용되는 정책 캐시(Manager)를 제어함.
 */
@Service
@RequiredArgsConstructor
public class PointPolicyService {

    private final PointPolicyRepository pointPolicyRepository;
    private final PointPolicyManager pointPolicyManager;

    /**
     * 포인트 정책 동적 업데이트
     * - 트랜잭션: DB 저장과 캐시 갱신을 원자적으로 처리함.
     * - 특징: 요청된 파라미터(maxEarnAmount, maxPossessionLimit, defaultExpireDays) 중 NULL인 항목은 기존 정책 값을 유지하여 **부분 업데이트(Partial Update)**를 지원함.
     **/
    @Transactional
    public void updatePolicy(PointPolicyUpdateRequest request) {

        // DTO의 @AssertTrue가 1차 검증하지만, 서비스에서도 한 번 더 확인하여 방어
        if (request.maxEarnAmount() == null &&
                request.maxPossessionLimit() == null &&
                request.defaultExpireDays() == null) {
            throw BusinessException.invalid("변경할 정책 값이 최소 하나 이상 필요합니다.");
        }

        // 1. 기존의 최신 정책을 가져온다. (없을 경우 예외 처리)
        PointPolicy currentPolicy = pointPolicyRepository.findTopByOrderByIdDesc()
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        // 2. 요청 파라미터가 있으면(not null) 새 값으로, 없으면(null) 기존 값(current)으로 설정

        // 1회 최대적립금액
        long newMaxEarnAmount = request.maxEarnAmount() != null
                ? request.maxEarnAmount() : currentPolicy.getMaxEarnAmount();

        // 개인별 최대 소유한도금액
        long newMaxPossessionLimit = request.maxPossessionLimit() != null
                ? request.maxPossessionLimit() : currentPolicy.getMaxPossessionLimit();

        // 포인트 만료일자
        int newDefaultExpireDays = request.defaultExpireDays() != null
                ? request.defaultExpireDays() : currentPolicy.getDefaultExpireDays();

        // 3. 병합된 값으로 새로운 정책 객체 생성 (이때 생성자 내부 유효성 체크 수행)
        PointPolicy newPolicy = new PointPolicy(newMaxEarnAmount, newMaxPossessionLimit, newDefaultExpireDays);

        // 4. 저장 및 캐시 갱신
        pointPolicyRepository.save(newPolicy);
        pointPolicyManager.refresh();
    }
}