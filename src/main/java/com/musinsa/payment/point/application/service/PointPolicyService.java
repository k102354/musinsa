package com.musinsa.payment.point.application.service;

import com.musinsa.payment.point.api.dto.PointPolicyUpdateRequest;
import com.musinsa.payment.point.domain.entity.PointPolicy;
import com.musinsa.payment.point.domain.repository.PointPolicyRepository;
import com.musinsa.payment.point.global.policy.PointPolicyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointPolicyService {

    private final PointPolicyRepository pointPolicyRepository;
    private final PointPolicyManager pointPolicyManager;

    /** 
     * 최대적립금액, 개인별 소유한도금액, 포인트 만료일자를 설정할 수 있다.
     * 만약 3개중 한개의 정책만 수정할 경우 이전 정책을 가져와서 입력받은 새로운 정책만 적용한다.
     **/
    @Transactional
    public void updatePolicy(PointPolicyUpdateRequest request) {

        if (request.maxEarnAmount() == null &&
                request.maxPossessionLimit() == null &&
                request.defaultExpireDays() == null) {
            throw new IllegalArgumentException("변경할 정책 값이 최소 하나 이상 필요합니다.");
        }

        // 1. 기존의 최신 정책을 가져옵니다. (없을 경우 예외 처리 or 기본값)
        PointPolicy currentPolicy = pointPolicyRepository.findTopByOrderByIdDesc()
                .orElseThrow(() -> new IllegalStateException("기존 정책이 존재하지 않습니다."));

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

        // 3. 병합된 값으로 새로운 정책 객체 생성 (이때 생성자 내부 validate 로직이 정합성 재검증 수행)
        PointPolicy newPolicy = new PointPolicy(newMaxEarnAmount, newMaxPossessionLimit, newDefaultExpireDays);

        // 4. 저장 및 캐시 갱신
        pointPolicyRepository.save(newPolicy);
        pointPolicyManager.refresh();
    }
}