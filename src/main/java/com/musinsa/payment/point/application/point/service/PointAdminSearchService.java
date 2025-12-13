package com.musinsa.payment.point.application.point.service;

import com.musinsa.payment.point.api.point.dto.PointBalanceResponse;
import com.musinsa.payment.point.api.point.dto.PointHistoryResponse;
import com.musinsa.payment.point.api.point.dto.PointStatisticsResponse;
import com.musinsa.payment.point.domain.point.entity.UserPointWallet;
import com.musinsa.payment.point.domain.point.enums.PointStatus;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.domain.point.repository.PointHistoryRepository;
import com.musinsa.payment.point.domain.point.repository.PointItemRepository;
import com.musinsa.payment.point.domain.point.repository.UserPointWalletRepository;
import com.musinsa.payment.point.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 포인트 관리자 조회 Service (Admin Search)
 * - 역할: 운영팀, CS팀, 재무팀 등을 위한 통합 조회 및 통계 지표 산출.
 * - 특징: 사용자별 제약(3개월)이 없으며, userId는 선택적 필터링 요소임.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointAdminSearchService {

    private final PointHistoryRepository pointHistoryRepository;
    private final PointItemRepository pointItemRepository;
    private final UserPointWalletRepository userPointWalletRepository;

    /**
     * [관리자] 포인트 이력 통합 조회
     * - 규칙: 조회 기간(startDate, endDate)은 필수로 요구됨.
     * - 특징: userId, refId 등은 선택적 필터링으로 처리됨.
     * */
    public Page<PointHistoryResponse> getHistories(
            LocalDate startDate,
            LocalDate endDate,
            Long userId,
            String refId,   // [추가]
            PointType type, // [추가]
            Pageable pageable
    ) {
        // 관리자는 날짜 필수 체크만 진행 (Repository의 idx_date 인덱스 활용을 위해)
        if (startDate == null || endDate == null) {
            throw BusinessException.invalid("조회 기간은 필수입니다.");
        }

        return pointHistoryRepository.findAllByAdminCondition(
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59),
                userId,
                refId,
                type,
                pageable
        ).map(PointHistoryResponse::from);
    }


    /**
     * [관리자] 시스템 전체 잔여 포인트 조회 
     * - 현재 사용가능한 잔액 총액 조회
     * - 성능: PointItem 테이블에서 AVAILABLE 상태의 잔액만 SUM하여 계산 (인덱스 활용).
     */
    public long getTotalRemain() {
        // PointItem 상태가 AVAILABLE 인 것만 합산
        return pointItemRepository.sumTotalRemainAmountByStatus(PointStatus.AVAILABLE);
    }

    /**
     * [관리자] 기간별 포인트 통계
     * - 총 적립액 vs 총 사용액 등
     * - 규칙: 조회 기간은 필수 파라미터.
     */
    public List<PointStatisticsResponse> getStatistics(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("통계 조회 기간은 필수입니다.");
        }

        return pointHistoryRepository.getStatisticsByType(
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59)
        );
    }

    /**
     * [관리자] 특정 사용자의 현재 잔액 조회
     * - 특정 유저의 현재 잔액을 확인.
     */
    public PointBalanceResponse getUserBalance(Long userId) {
        UserPointWallet wallet = userPointWalletRepository.findByUserId(userId)
                .orElse(new UserPointWallet(userId, 0L));

        return PointBalanceResponse.builder()
                .userId(userId)
                .currentBalance(wallet.getBalance())
                .build();
    }
}