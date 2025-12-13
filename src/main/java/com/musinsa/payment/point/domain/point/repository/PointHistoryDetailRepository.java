package com.musinsa.payment.point.domain.point.repository;

import com.musinsa.payment.point.domain.point.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 포인트 거래 상세 내역 리포지토리 (Detail Repository)
 * - PointHistory와 PointItem을 연결하는 상세 이력(매핑 테이블) 관리를 담당.
 * - 주로 PointHistory의 Cascade 옵션을 통해 저장되므로, 단독 조회 메서드는 거의 사용하지 않음.
 */
public interface PointHistoryDetailRepository extends JpaRepository<PointHistory, Long> {
}