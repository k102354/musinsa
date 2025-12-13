package com.musinsa.payment.point.domain.point.entity;

import com.musinsa.payment.point.domain.common.BaseTimeEntity;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.global.util.TsidUtil;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_history", indexes = {
        @Index(name = "idx_user_ref", columnList = "userId, refId") // 주문번호로 조회
})
public class PointHistory extends BaseTimeEntity {

    @Id @Column(name = "point_history_id")
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType type; // USE, USE_CANCEL, SAVE, etc.

    @Column(nullable = false)
    private long amount; // 이 거래의 총액

    @Column(name = "ref_id")
    private String refId; // 주문번호

    // Master-Detail 관계 (영속성 전이: History 저장 시 Detail도 같이 저장됨)
    @OneToMany(mappedBy = "pointHistory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PointHistoryDetail> details = new ArrayList<>();

    @PrePersist
    private void generateId() {
        if (this.id == null) this.id = TsidUtil.nextId();
    }

    @Builder
    public PointHistory(Long userId, PointType type, long amount, String refId) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.refId = refId;
    }

    // 연관관계 편의 메서드
    public void addDetail(PointHistoryDetail detail) {
        this.details.add(detail);
        detail.setPointHistory(this);
    }
}