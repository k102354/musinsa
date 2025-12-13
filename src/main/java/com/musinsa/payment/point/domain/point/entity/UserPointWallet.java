package com.musinsa.payment.point.domain.point.entity;

import com.musinsa.payment.point.domain.common.BaseTimeEntity;
import com.musinsa.payment.point.global.error.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 포인트 지갑 (User Point Wallet)
 * 사용자별 현재 총 잔액(balance)을 관리하는 요약 테이블 (Cache-aside 패턴의 캐시 테이블 역할).
 * - 트랜잭션: Lock을 통해 동시성 제어가 필수적이며, 잔액 조회(마이페이지) 시 성능 최적화를 위해 사용됨.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_point_wallet") // 
public class UserPointWallet extends BaseTimeEntity {

    // User ID가 PK이며, User 테이블과의 1:1 관계를 가짐 (User 테이블을 참조하지 않고 ID만 저장)
    @Id
    @Column(name = "user_id")
    private Long userId; 

    @Column(nullable = false)
    private long balance; // 사용자 총 잔액 합계
    
    public UserPointWallet(Long userId, long balance) {
        this.userId = userId;
        this.balance = balance;
    }

    /**
     * 포인트 적립
     * - 잔액 증가 시 사용.
     * @param maxLimit 개인별 최대 보유 한도 (정책 반영)
     */
    public void earn(long amount, long maxLimit) {
        if (amount <= 0) {
            throw BusinessException.invalid("적립 금액은 0보다 커야 합니다.");
        }
        // 최대 한도 체크
        if (this.balance + amount > maxLimit) {
            throw BusinessException.invalid("개인별 최대 보유 한도를 초과했습니다.");
        }
        this.balance += amount;
    }

    /**
     * 포인트 사용
     * - 잔액 감소 시 사용.
     * - 이 메서드를 호출하기 전, PointItem에서 차감할 금액이 충분한지 이미 계산되어야 함.
     */
    public void use(long amount) {
        if (amount <= 0) {
            throw BusinessException.invalid("사용 금액은 0보다 커야 합니다.");
        }
        // 잔액 부족 체크
        if (this.balance < amount) {
            throw BusinessException.invalid("포인트 잔액이 부족합니다."); // 잔액 부족 체크
        }
        this.balance -= amount;
    }
}