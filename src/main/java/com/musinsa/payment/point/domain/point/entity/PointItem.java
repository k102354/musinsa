package com.musinsa.payment.point.domain.point.entity;

import com.musinsa.payment.point.domain.common.BaseTimeEntity;
import com.musinsa.payment.point.domain.point.enums.PointStatus;
import com.musinsa.payment.point.global.error.BusinessException;
import com.musinsa.payment.point.global.util.TsidUtil;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_item", indexes = {
        // 만료 임박 순 조회 및 유효한 포인트 조회를 위한 복합 인덱스
        @Index(name = "idx_user_status_expire", columnList = "userId, status, expireAt")
})
public class PointItem extends BaseTimeEntity {

    @Id
    @Column(name = "point_item_id")
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long originalAmount; // 최초 지급액

    @Column(nullable = false)
    private long remainAmount;   // 현재 잔액

    @Column(nullable = false)
    private LocalDateTime expireAt;

    @Column(nullable = false)
    private boolean isManual; // 수기 지급 여부

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointStatus status; // AVAILABLE, EXHAUSTED, EXPIRED, CANCELED

    @PrePersist
    private void generateId() {
        if (this.id == null) {
            this.id = TsidUtil.nextId();
        }
    }

    @Builder
    public PointItem(Long userId, long originalAmount, LocalDateTime expireAt, boolean isManual) {
        if (userId == null) throw BusinessException.invalid("유저 ID는 필수입니다.");
        if (originalAmount < 1) throw BusinessException.invalid("적립액은 1원 이상이어야 합니다.");
        if (expireAt == null || expireAt.isBefore(LocalDateTime.now())) {
            throw BusinessException.invalid("유효기간은 현재 시간보다 미래여야 합니다.");
        }

        this.userId = userId;
        this.originalAmount = originalAmount;
        this.remainAmount = originalAmount;
        this.expireAt = expireAt;
        this.isManual = isManual;
        this.status = PointStatus.AVAILABLE;
    }

    // --- 비즈니스 로직 ---

    /**
     * 포인트 차감 (사용)
     */
    public void use(long amount) {
        if (this.status != PointStatus.AVAILABLE) {
            throw BusinessException.invalid("사용 가능한 상태의 포인트가 아닙니다.");
        }
        if (isExpired()) {
            throw BusinessException.invalid("이미 만료된 포인트입니다.");
        }
        if (amount > this.remainAmount) {
            throw BusinessException.invalid("차감하려는 금액이 잔액보다 큽니다.");
        }

        this.remainAmount -= amount;

        if (this.remainAmount == 0) {
            this.status = PointStatus.EXHAUSTED;
        }
    }

    /**
     * 포인트 복구 (취소 시)
     */
    public void cancel(long amount) {
        // 정책: 만료된 포인트는 복구 불가 (필요 시 정책에 따라 수정 가능)
        if (isExpired()) {
            throw BusinessException.invalid("만료된 포인트는 복구할 수 없습니다.");
        }
        if (this.remainAmount + amount > this.originalAmount) {
            throw BusinessException.invalid("원금보다 더 많이 복구할 수 없습니다.");
        }

        this.remainAmount += amount;

        // 소진 상태였다면 다시 사용 가능 상태로 복귀
        if (this.status == PointStatus.EXHAUSTED) {
            this.status = PointStatus.AVAILABLE;
        }
    }

    /**
     * 적립 취소 (관리자/시스템)
     */
    public void cancelEarn() {
        if (this.remainAmount != this.originalAmount) {
            throw BusinessException.invalid("이미 사용된 포인트는 적립 취소할 수 없습니다.");
        }
        this.status = PointStatus.CANCELED;
        this.remainAmount = 0;
    }

    public void expire() {
        if (this.remainAmount > 0 && this.status == PointStatus.AVAILABLE) {
            this.status = PointStatus.EXPIRED;
            this.remainAmount = 0;
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expireAt);
    }

    public void setExpired() {
        this.expireAt = LocalDateTime.now().minusDays(1);
    }
}