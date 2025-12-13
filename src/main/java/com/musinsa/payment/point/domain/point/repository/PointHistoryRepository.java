package com.musinsa.payment.point.domain.point.repository;

import com.musinsa.payment.point.domain.point.entity.PointHistory;
import com.musinsa.payment.point.domain.point.enums.PointType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    /**
     * 중복 검사
     * - 특정 주문(refId)에 대해 해당 타입(USE 등)의 처리가 이미 존재하는지 확인
     */
    boolean existsByUserIdAndRefIdAndType(Long userId, String refId, PointType type);

    /**
     * 히스토리 + 상세 내역(Detail) 한 방 조회 (Fetch Join)
     * - 목적: 포인트 사용 취소(USE_CANCEL) 등을 위해 원본 이력과 상세 내역을 함께 로딩
     * - 인덱스 활용을 위해 userId 필수 포함
     */
    @Query("SELECT h FROM PointHistory h " +
            "JOIN FETCH h.details " +
            "WHERE h.userId = :userId AND h.refId = :refId AND h.type = :type")
    Optional<PointHistory> findByUserIdAndRefIdAndTypeWithDetails(
            @Param("userId") Long userId,
            @Param("refId") String refId,
            @Param("type") PointType type
    );

    /**
     * 특정 유저 + 주문번호 + 여러 타입(IN 절)에 대한 합계 조회
     * - userId 추가: 인덱스(idx_user_ref) 활용 및 타인 데이터 접근 방지
     * - COALESCE 사용: 결과가 없으면 NULL 대신 0 반환
     */
    @Query("SELECT COALESCE(SUM(h.amount), 0) " +
            "FROM PointHistory h " +
            "WHERE h.userId = :userId AND h.refId = :refId AND h.type IN :types")
    long getSumAmountByUserIdAndRefIdAndTypes(
            @Param("userId") Long userId,   // 추가됨
            @Param("refId") String refId,
            @Param("types") List<PointType> types
    );
}