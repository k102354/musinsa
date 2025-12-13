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
     * 멱등성 검사
     * - 특정 주문(refId)에 대해 해당 타입(USE 등)의 처리가 이미 존재하는지 확인
     */
    boolean existsByRefIdAndType(String refId, PointType type);

    /**
     * 히스토리 + 상세 내역(Detail) 한 방 조회 (Fetch Join)
     * - N+1 문제 해결
     * - 포인트 사용 취소(cancelUse) 시, 원본의 상세 내역을 순회해야 하므로 필수적임
     */
    @Query("SELECT h FROM PointHistory h " +
            "JOIN FETCH h.details " +  // details 컬렉션을 즉시 로딩
            "WHERE h.refId = :refId AND h.type = :type")
    Optional<PointHistory> findByRefIdAndTypeWithDetails(@Param("refId") String refId, @Param("type") PointType type);

    /**
     * [변경] 여러 타입(IN 절)에 대한 합계를 한 번에 조회
     * COALESCE 사용: 결과가 없으면 NULL 대신 0 반환
     */
    @Query("SELECT COALESCE(SUM(h.amount), 0) " +
            "FROM PointHistory h " +
            "WHERE h.refId = :refId AND h.type IN :types")
    long getSumAmountByRefIdAndTypes(@Param("refId") String refId,
                                     @Param("types") List<PointType> types);
}