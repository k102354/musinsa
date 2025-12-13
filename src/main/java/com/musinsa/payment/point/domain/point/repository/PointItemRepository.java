package com.musinsa.payment.point.domain.point.repository;

import com.musinsa.payment.point.domain.point.entity.PointItem;
import com.musinsa.payment.point.domain.point.enums.PointStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PointItemRepository extends JpaRepository<PointItem, Long> {

    /**
     * 포인트 차감 우선순위 조회 (Use/Refund 시 사용)
     * 사용자에게 부여된 PointItem 중, 어떤 것부터 소진해야 하는지 결정
     * - 1순위: 관리자 수기 지급 (isManual = true) -> Boolean DESC (우선 소진 정책)
     * - 2순위: 유효기간 임박 (expireAt ASC) -> FIFO와 만료일 임박 정책 결합
     * - 인덱스 활용: idx_user_status_expire (userId, status, expireAt)를 활용하여 효율적인 정렬 및 조회
     */
    List<PointItem> findByUserIdAndStatusAndExpireAtAfterOrderByIsManualDescExpireAtAsc(
            Long userId,
            PointStatus status, // 반드시 AVAILABLE 상태여야 함
            LocalDateTime now // 현재 시간보다 만료일이 미래인(유효한) 아이템만 조회
    );

    /**
     * [사용자] 소멸 예정 포인트 조회 (마이페이지/알림용)
     * 사용자에게 곧 만료될 예정인 포인트 목록을 만료일 순으로 제공
     * - 인덱스 활용: idx_user_status_expire (userId, status, expireAt)
     * - 조건:
     * 1. userId (필수)
     * 2. status = AVAILABLE (사용 가능한 상태)
     * 3. expireAt BETWEEN :now AND :limitDate (만료 임박 기간 설정, e.g., 30일)
     * 4. remainAmount > 0 (실제 잔액이 남아있는 포인트만 조회)
     */
    @Query("SELECT i FROM PointItem i " +
            "WHERE i.userId = :userId " +
            "AND i.status = :status " +
            "AND i.expireAt BETWEEN :now AND :limitDate " +
            "AND i.remainAmount > 0 " +
            "ORDER BY i.expireAt ASC")
    List<PointItem> findExpiringPoints(
            @Param("userId") Long userId,
            @Param("status") PointStatus status,
            @Param("now") LocalDateTime now,
            @Param("limitDate") LocalDateTime limitDate
    );

    /**
     * [관리자] 시스템 전체 잔여 포인트 합계 조회
     * 포인트 전체 잔액 산출
     * - 조건: 상태가 AVAILABLE(사용 가능)인 PointItem의 잔액(remainAmount)만 합산
     * - COALESCE 사용: 조회 결과가 0건일 경우 NULL 대신 0을 반환하여 서비스 계층의 예외를 방지
     */
    @Query("SELECT COALESCE(SUM(i.remainAmount), 0) FROM PointItem i WHERE i.status = :status")
    long sumTotalRemainAmountByStatus(@Param("status") PointStatus status);
}