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

/**
 * 포인트 거래 이력 (Point History)
 * - 포인트 거래 발생 시마다 기록되는 **영수증(기록)** 엔티티.
 * - PointItem의 상태 변경과는 별개로, '무슨 일이 언제 일어났는지'에 대한 불변의 기록을 제공함.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_history", indexes = {
        @Index(name = "idx_user_ref", columnList = "userId, refId"), // 주문번호로 조회
        @Index(name = "idx_user_date", columnList = "userId, createdAt"), // 내역 기간 조회용
        @Index(name = "idx_date", columnList = "createdAt") // 관리자 통계/조회용
})
public class PointHistory extends BaseTimeEntity {

    @Id @Column(name = "point_history_id")
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType type; // USE, USE_CANCEL, EARN, EARN_CANCEL ....

    @Column(nullable = false)
    private long amount; // 이 거래의 총액

    @Column(name = "ref_id")
    private String refId; // 주문번호, 이벤트 적립번호 등

    // Master-Detail 관계 (영속성 전이: History 저장 시 Detail도 같이 저장됨)
    // History가 Detail의 생명주기를 관리함 (orphanRemoval = true)
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

    /**
     * 연관관계 편의 메서드 (양방향 연결)
     * - History 생성 시 Detail도 함께 등록하도록 함.
     */
    public void addDetail(PointHistoryDetail detail) {
        this.details.add(detail);
        detail.setPointHistory(this);
    }

}