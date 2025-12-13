package com.musinsa.payment.point.domain.point.entity;

import com.musinsa.payment.point.domain.common.BaseTimeEntity;
import com.musinsa.payment.point.global.error.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_point_wallet") // 
public class UserPointWallet extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId; 

    @Column(nullable = false)
    private long balance;
    
    public UserPointWallet(Long userId, long balance) {
        this.userId = userId;
        this.balance = balance;
    }

    /**
     * 포인트 적립 (입금)
     */
    public void earn(long amount, long maxLimit) {
        if (amount <= 0) {
            throw BusinessException.invalid("적립 금액은 0보다 커야 합니다.");
        }
        if (this.balance + amount > maxLimit) {
            throw BusinessException.invalid("개인별 최대 보유 한도를 초과했습니다.");
        }
        this.balance += amount;
    }

    /**
     * 포인트 사용 (출금)
     */
    public void use(long amount) {
        if (amount <= 0) {
            throw BusinessException.invalid("사용 금액은 0보다 커야 합니다.");
        }
        if (this.balance < amount) {
            throw BusinessException.invalid("포인트 잔액이 부족합니다."); // 잔액 부족 체크
        }
        this.balance -= amount;
    }
}