package com.musinsa.payment.point.domain.policy.entity;

import com.musinsa.payment.point.domain.common.BaseTimeEntity;
import com.musinsa.payment.point.global.error.BusinessException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** * 포인트 기본 정책 설정 (정책 이력 테이블)
 * - 역할: 서비스의 핵심 비즈니스 정책 값들을 영속화하고, 변경 이력을 관리함.
 * - 특징: DB에 새로운 row가 추가되는 방식(Append-only)으로 관리되어 정책 변경의 투명성을 확보함.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "point_policy")
public class PointPolicy extends BaseTimeEntity {

    // 요구사항에 명시된 절대적 기준 (하드코딩 방지를 위한 상수)
    private static final long MIN_SAFE_EARN_AMOUNT = 1L;       // 최소 적립 1원
    private static final long MAX_SAFE_EARN_AMOUNT = 100_000L; // 최대 적립 10만 원
    private static final int MIN_EXPIRE_DAYS = 1;              // 최소 만료 1일
    private static final int MAX_EXPIRE_DAYS = 1825;           // 최대 만료 5년 (365 * 5)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 정책 버전 ID

    @Column(nullable = false)
    private long minEarnAmount; // 최소 적립액 (현재 1원으로 고정)

    @Column(nullable = false)
    private long maxEarnAmount; // 1회 최대 적립액

    @Column(nullable = false)
    private long maxPossessionLimit; // 개인별 최대 보유 한도

    @Column(nullable = false)
    private int defaultExpireDays; // 기본 만료 일자

    /**
     * 모든 필드를 받는 생성자 (DB 직접 삽입, 테스트 등 활용)
     */
    public PointPolicy(long minEarnAmount, long maxEarnAmount, long maxPossessionLimit, int defaultExpireDays) {
        // 유효성 체크 진행
        validate(maxEarnAmount, maxPossessionLimit, defaultExpireDays);

        this.minEarnAmount = minEarnAmount;
        this.maxEarnAmount = maxEarnAmount;
        this.maxPossessionLimit = maxPossessionLimit;
        this.defaultExpireDays = defaultExpireDays;
    }

    /**
     * 서비스에서 사용하는 정책 생성자 (minEarnAmount는 상수로 고정)
     * - Service 계층에서 정책 업데이트 시 이 생성자를 호출하며, 이 때 validate()가 수행됨.
     */
    public PointPolicy(long maxEarnAmount, long maxPossessionLimit, int defaultExpireDays) {
        // 유효성 체크 진행
        validate(maxEarnAmount, maxPossessionLimit, defaultExpireDays);

        this.minEarnAmount = MIN_SAFE_EARN_AMOUNT;
        this.maxEarnAmount = maxEarnAmount;
        this.maxPossessionLimit = maxPossessionLimit;
        this.defaultExpireDays = defaultExpireDays;
    }

    /**
     * 정책 값의 유효성 체크 (Policy Boundary Check)
     * - DTO에서 1차 체크 후, DB 저장 직전 최종 도메인 규칙을 여기서 강제함.
     **/
    private void validate(long maxEarn, long maxPossession, int expireDays) {
        // 1. 최대 적립 금액 상한 체크
        if (maxEarn > MAX_SAFE_EARN_AMOUNT) {
            throw BusinessException.invalid("1회 최대 적립액은 " + MAX_SAFE_EARN_AMOUNT + "원을 초과할 수 없습니다.");
        }

        // 2. 소유 한도와 1회 적립액 간의 논리적 체크
        if (maxPossession < maxEarn) {
            throw BusinessException.invalid("최대 소유 한도가 1회 적립액보다 작을 수 없습니다.");
        }

        // 3. 만료일 유효 범위 체크
        if (expireDays < MIN_EXPIRE_DAYS || expireDays >= MAX_EXPIRE_DAYS) {
            throw BusinessException.invalid("만료 기간은 " + MIN_EXPIRE_DAYS + "일 이상, 5년 미만이어야 합니다.");
        }
    }
}