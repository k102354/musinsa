package com.musinsa.payment.point.domain.point.entity;

import com.musinsa.payment.point.domain.common.BaseTimeEntity;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.global.util.TsidUtil;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포인트 거래 상세 내역 (Detail)
 * - PointHistory(거래)와 PointItem(원장)을 연결하는 매핑 테이블.
 * - 주로 포인트 사용(USE) 거래에서 '어떤 PointItem을 얼마만큼 사용했는지' 기록하는 데 사용됨.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_history_detail")
public class PointHistoryDetail extends BaseTimeEntity {

    @Id @Column(name = "point_history_detail_id")
    private Long id;

    // PointHistory 참조: FecthType.LAZY로 지연 로딩
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_history_id", nullable = false)
    private PointHistory pointHistory;

    // PointItem 참조: 어떤 원장(Item)의 잔액이 변동되었는지 기록
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_item_id", nullable = false)
    private PointItem pointItem;

    @Column(nullable = false)
    private long amount; // 이 Item에서 사용/복구된 금액

    // 만료된 포인트 취소(복구) 시, 원본이 되는(복구 대상) PointItem의 ID
    // 데이터 추적용으로 남겨둠
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