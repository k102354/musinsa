package com.musinsa.payment.point.domain.point.entity;

import com.musinsa.payment.point.domain.common.BaseTimeEntity;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.global.util.TsidUtil;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_history_detail")
public class PointHistoryDetail extends BaseTimeEntity {

    @Id @Column(name = "point_history_detail_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_history_id", nullable = false)
    private PointHistory pointHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_item_id", nullable = false)
    private PointItem pointItem;

    @Column(nullable = false)
    private long amount; // 이 아이템에서 사용된 금액

    // 만료된 포인트 취소(복구) 시, 원본이 되는(복구 대상) PointItem의 ID
    @Column(name = "restored_from_item_id")
    private Long restoredFromItemId;

    @PrePersist
    private void generateId() {
        if (this.id == null) this.id = TsidUtil.nextId();
    }

    @Builder
    public PointHistoryDetail(PointItem pointItem, long amount, Long restoredFromItemId) {
        this.pointItem = pointItem;
        this.amount = amount;
        this.restoredFromItemId = restoredFromItemId;
    }

    public void setPointHistory(PointHistory pointHistory) {
        this.pointHistory = pointHistory;
    }
}