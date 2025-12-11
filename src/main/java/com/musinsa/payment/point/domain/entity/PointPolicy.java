package com.musinsa.payment.point.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 
 * 포인트 기본 정책 설정
 * */
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
    private Long id;

    @Column(nullable = false)
    private long minEarnAmount;

    @Column(nullable = false)
    private long maxEarnAmount;

    @Column(nullable = false)
    private long maxPossessionLimit;

    @Column(nullable = false)
    private int defaultExpireDays;


    public PointPolicy(long maxEarnAmount, long maxPossessionLimit, int defaultExpireDays) {
        // 이곳에서 잘못된 값이 들어오는 것을 체크한다.
        validate(maxEarnAmount, maxPossessionLimit, defaultExpireDays);

        this.minEarnAmount = MIN_SAFE_EARN_AMOUNT;
        this.maxEarnAmount = maxEarnAmount;
        this.maxPossessionLimit = maxPossessionLimit;
        this.defaultExpireDays = defaultExpireDays;
    }

    /**
     * 기본 정책설정의 유효성 체크를 수행한다.
     * 최소적립금액, 최대적립금액, 최소만료일, 최대만료일
     **/
    private void validate(long maxEarn, long maxPossession, int expireDays) {
        // 1. 적립 금액 범위 체크 (요구사항 1.1.1)
        if (maxEarn > MAX_SAFE_EARN_AMOUNT) {
            throw new IllegalArgumentException("1회 최대 적립액은 " + MAX_SAFE_EARN_AMOUNT + "원을 초과할 수 없습니다.");
        }

        // 2. 소유 한도 체크
        if (maxPossession < maxEarn) { // (상식적 방어)
            throw new IllegalArgumentException("개인 보유 한도가 1회 적립액보다 작을 수 없습니다.");
        }

        // 3. 만료일 체크 (요구사항 1.5)
        if (expireDays < MIN_EXPIRE_DAYS || expireDays >= MAX_EXPIRE_DAYS) {
            throw new IllegalArgumentException("만료 기간은 " + MIN_EXPIRE_DAYS + "일 이상, 5년 미만이어야 합니다.");
        }
    }
}