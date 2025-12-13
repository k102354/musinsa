package com.musinsa.payment.point.domain.point.repository;

import com.musinsa.payment.point.api.point.dto.PointStatisticsResponse;
import com.musinsa.payment.point.domain.point.entity.PointHistory;
import com.musinsa.payment.point.domain.point.enums.PointType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    /**
     * 중복 검사 (비즈니스 규칙 방어)
     * - idempotent(멱등성) 보장. 특정 주문(refId)에 대해 동일한 타입(USE, SAVE 등)의 처리가 이미 수행되었는지 확인
     * - 인덱스 활용: idx_user_ref (userId, refId) 활용
     */
    boolean existsByUserIdAndRefIdAndType(Long userId, String refId, PointType type);

    /**
     * 히스토리 + 상세 내역(Detail) 한 방 조회 (Fetch Join)
     * - 포인트 사용 취소(USE_CANCEL) 등의 복잡한 로직 수행 시, N+1 문제 없이 원본 이력과 상세 내역을 함께 로딩
     * - 인덱스 활용: idx_user_ref (userId, refId)를 활용
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
     * - 특정 주문/이벤트에 대해 총 얼마나 사용/취소 되었는지 합계 계산 (데이터 정합성 검증용)
     * - 인덱스 활용: idx_user_ref (userId, refId) 활용
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

    /**
     * [사용자용] 내 거래내역 조회
     * - 마이페이지 포인트 내역 조회 (기간 제한 규칙은 Service 계층에서 적용)
     * - 인덱스 활용: idx_user_date (userId, createdAt)가 가장 유리하며, refId나 type은 동적 필터링으로 처리
     * - 조건: userId와 기간(startDt, endDt)은 필수, refId와 type은 동적 (NULL일 때 무시)
     */
    @Query("SELECT h FROM PointHistory h " +
            "WHERE h.userId = :userId " +
            "AND h.createdAt BETWEEN :startDt AND :endDt " +
            "AND (:refId IS NULL OR h.refId = :refId) " + // [추가] 거래번호
            "AND (:type IS NULL OR h.type = :type)")      // [추가] 거래상태
    Page<PointHistory> findAllByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("startDt") LocalDateTime startDt,
            @Param("endDt") LocalDateTime endDt,
            @Param("refId") String refId,
            @Param("type") PointType type,
            Pageable pageable
    );

    /**
     * [관리자용] 전체 거래내역 조회 (통합 검색)
     * - 운영/CS팀의 광범위한 포인트 이력 검색
     * - 인덱스 활용: idx_date (createdAt)를 기본으로 하고, userId가 제공되면 추가적으로 인덱스가 활용됨.
     * - 조건: 기간(startDt, endDt)은 필수, userId, refId, type은 선택적 동적 필터링
     */
    @Query("SELECT h FROM PointHistory h " +
            "WHERE h.createdAt BETWEEN :startDt AND :endDt " +
            "AND (:userId IS NULL OR h.userId = :userId) " +
            "AND (:refId IS NULL OR h.refId = :refId) " + // [추가] 거래번호
            "AND (:type IS NULL OR h.type = :type)")      // [추가] 거래상태
    Page<PointHistory> findAllByAdminCondition(
            @Param("startDt") LocalDateTime startDt,
            @Param("endDt") LocalDateTime endDt,
            @Param("userId") Long userId,
            @Param("refId") String refId,
            @Param("type") PointType type,
            Pageable pageable
    );

    /**
     * [관리자] 특정 기간 동안의 타입별 합계 통계
     * - 월별/일별 총 적립액(SAVE) 및 총 사용액(USE) 등을 산출 (대시보드 지표)
     * - SELECT NEW 사용: DTO 객체로 결과를 직접 매핑하여 반환 (성능 최적화)
     * - 인덱스 활용: idx_date (createdAt)
     */
    @Query("SELECT new com.musinsa.payment.point.api.point.dto.PointStatisticsResponse(h.type, SUM(h.amount)) " +
            "FROM PointHistory h " +
            "WHERE h.createdAt BETWEEN :startDt AND :endDt " +
            "GROUP BY h.type")
    List<PointStatisticsResponse> getStatisticsByType(
            @Param("startDt") LocalDateTime startDt,
            @Param("endDt") LocalDateTime endDt
    );
}