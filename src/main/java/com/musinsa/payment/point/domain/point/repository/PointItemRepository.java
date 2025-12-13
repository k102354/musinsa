package com.musinsa.payment.point.domain.point.repository;

import com.musinsa.payment.point.domain.point.entity.PointItem;
import com.musinsa.payment.point.domain.point.enums.PointStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PointItemRepository extends JpaRepository<PointItem, Long> {

    /**
     * 포인트 차감 우선순위 조회
     * 1순위: 관리자 수기 지급 (isManual = true) -> Boolean DESC
     * 2순위: 유효기간 임박 (expireAt ASC)
     */
    // [변경] 현재 시간보다 만료일이 미래인(After) 것만 가져오기
    List<PointItem> findByUserIdAndStatusAndExpireAtAfterOrderByIsManualDescExpireAtAsc(
            Long userId,
            PointStatus status,
            LocalDateTime now // 현재 시간 파라미터 추가
    );
}