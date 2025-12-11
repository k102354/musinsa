package com.musinsa.payment.point.global.policy;

import com.musinsa.payment.point.domain.entity.PointPolicy;
import com.musinsa.payment.point.domain.repository.PointPolicyRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기본 포인트 정책을 로드하거나 데이터베이스에 설정된 포인트 적립 정책을 가져온다.
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointPolicyManager {

    private final PointPolicyRepository pointPolicyRepository;

    // 캐싱된 정책 값 (외부에서 이 Getter를 호출해서 사용)
    @Getter private long minEarnAmount;
    @Getter private long maxEarnAmount;
    @Getter private long maxPossessionLimit;
    @Getter private int defaultExpireDays;

    /**
     * 어플리케이션 시작 시 딱 한 번 실행되어 DB 설정을 메모리로 로드한다.
     */
    @PostConstruct
    public void loadPolicy() {
        // DB에 정책이 없으면 기본값으로 생성해서 넣는 방어 로직
        PointPolicy policy = pointPolicyRepository.findTopByOrderByIdDesc()
                .orElseGet(() -> {
                    log.info("No policy found. Creating default policy.");
                    return pointPolicyRepository.save(new PointPolicy(100000L, 2000000L, 365));
                });

        this.minEarnAmount = policy.getMinEarnAmount();
        this.maxEarnAmount = policy.getMaxEarnAmount();
        this.maxPossessionLimit = policy.getMaxPossessionLimit();
        this.defaultExpireDays = policy.getDefaultExpireDays();

        log.info("Point Policy Loaded: MaxEarn={}, Limit={}", maxEarnAmount, maxPossessionLimit);
    }

    /**
     * (옵션) 운영 중 관리자가 DB를 바꾸고 이 메서드를 호출하면
     * 서버 재시작 없이 정책이 반영됨 (Admin API용)
     */
    public void refresh() {
        loadPolicy();
    }
}