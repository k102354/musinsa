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

/**
 * 포인트 원장 (Point Item)
 * - 역할: 사용자가 실제로 보유한 '포인트 낱장' 하나하나를 관리하는 핵심 엔티티.
 * - 잔액, 만료일, 사용 상태 등 모든 재무적 정보를 포함하며, 모든 포인트 사용/취소/만료의 대상이 됨.
 */
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
    private LocalDateTime expireAt; // 유효기간

    @Column(nullable = false)
    private boolean isManual; // 수기 지급 여부 (차감 우선순위 1순위 결정 요소)

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
        // 유효기간은 현재보다 미래여야 함
        if (expireAt == null || expireAt.isBefore(LocalDateTime.now())) {
            throw BusinessException.invalid("유효기간은 현재 시간보다 미래여야 합니다.");
        }

        this.userId = userId;
        this.originalAmount = originalAmount;
        this.remainAmount = originalAmount; // 초기 잔액 = 최초 지급액
        this.expireAt = expireAt;
        this.isManual = isManual;
        this.status = PointStatus.AVAILABLE;
    }

    /**
     * 포인트 차감 (사용)
     * - 잔액을 감소시키고, 잔액이 0이 되면 상태를 EXHAUSTED로 변경함.
     * - DB 업데이트(Dirty Checking)가 발생하는 핵심 쓰기 메서드.
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
            this.status = PointStatus.EXHAUSTED; // 잔액 모두 소진
        }
    }

    /**
     * 포인트 복구 (취소 시)
     * - 사용 취소(USE_CANCEL) 시, 사용되었던 금액만큼 잔액을 복원함.
     */
    public void cancel(long amount) {
        // 만료된 포인트는 복구 불가
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
     * - 전액이 남아있을 때만 가능. 남아있는 금액을 0으로 만들고 상태를 CANCELED로 변경.
     */
    public void cancelEarn() {
        if (this.remainAmount != this.originalAmount) {
            throw BusinessException.invalid("이미 사용된 포인트는 적립 취소할 수 없습니다.");
        }
        this.status = PointStatus.CANCELED;
        this.remainAmount = 0;
    }

    /**
     * 포인트 만료 처리 (배치/스케줄러에서 사용)
     * - 만료 시 잔액을 0으로, 상태를 EXPIRED로 변경.
     * - 0원인 포인트는 처리하지 않음.
     */
    public void expire() {
        if (this.remainAmount > 0 && this.status == PointStatus.AVAILABLE) {
            this.status = PointStatus.EXPIRED;
            this.remainAmount = 0;
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expireAt);
    }

    // 테스트 코드에서 만료 상태를 강제 설정하기 위한 유틸리티 메서드. 운영 코드에는 불필요
    public void setExpired() {
        this.expireAt = LocalDateTime.now().minusDays(1);
    }
}