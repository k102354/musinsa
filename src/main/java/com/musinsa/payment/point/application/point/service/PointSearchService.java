package com.musinsa.payment.point.application.point.service;

import com.musinsa.payment.point.api.point.dto.PointBalanceResponse;
import com.musinsa.payment.point.api.point.dto.PointExpiringResponse;
import com.musinsa.payment.point.api.point.dto.PointHistoryResponse;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 포인트 조회 Service (Point Search)
 * - 역할: 사용자 및 관리자의 포인트 이력/잔액 조회 트랜잭션을 관리.
 * - 특징: @Transactional(readOnly = true)를 설정하여 조회 성능을 최적화함.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointSearchService {

    private final PointHistoryRepository historyRepository;
    private final PointItemRepository pointItemRepository; // 변경됨
    private final UserPointWalletRepository userPointWalletRepository;

    /**
     * [사용자] 포인트 사용내역 조회
     * - 규칙 1: 조회 기간은 최대 3개월로 제한됨 (validateDateRange).
     * - 규칙 2: userId는 필수 파라미터임.
     * - 성능: userId와 기간(createdAt) 인덱스를 활용하여 효율적인 페이징 조회.
     */
    public Page<PointHistoryResponse> getMyHistories(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            String refId,
            PointType type,
            Pageable pageable
    ) {
        validateUserId(userId);
        validateDateRange(startDate, endDate); // 3개울 제한 규칙 적용

        return historyRepository.findAllByUserAndDateRange(
                userId,
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59),
                refId,
                type,
                pageable
        ).map(PointHistoryResponse::from);
    }

    /**
     * [사용자] 내 잔액 조회
     * - 성능: UserPointWallet 테이블을 바로 조회하여 빠른 응답 제공 (Lock 불필요)
     */
    public PointBalanceResponse getMyBalance(Long userId) {
        // 필수값 유저 ID 체크
        validateUserId(userId);

        UserPointWallet wallet = userPointWalletRepository.findByUserId(userId)
                .orElseThrow(() -> BusinessException.invalid("존재하지 않는 사용자입니다."));

        return PointBalanceResponse.builder()
                .userId(userId)
                .currentBalance(wallet.getBalance())
                .build();
    }

    /**
     * [사용자] 30일이내 소멸 예정 포인트를 조회한다.
     * - 고객에게 만료 임박 알림/경고 제공 (마케팅 및 CS 활용)
     * - 성능: PointItem의 idx_user_status_expire 인덱스를 활용하여 효율적인 범위 조회.
     */
    public List<PointExpiringResponse> getListExpiringPointItemsIn30Days(Long userId) {
        // 필수값 유저 ID 체크
        validateUserId(userId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysLater = now.plusDays(30);

        // PointItem에서 AVAILABLE 상태, 30일 이내 만료 예정인 PointItem을 조회
        return pointItemRepository.findExpiringPoints(userId, PointStatus.AVAILABLE, now, thirtyDaysLater).stream()
                .map(item -> PointExpiringResponse.builder()
                        .amount(item.getRemainAmount())     // 잔액
                        .expireDate(item.getExpireAt())     // 만료일 (expireAt)
                        .pointItemId(item.getId())             // PointItem ID
                        .build())
                .toList();
    }

    /**
     * 날짜 유효성 체크
     * - 규칙: 1. 종료일 >= 시작일, 2. 최대 조회 기간 3개월 제한
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw BusinessException.invalid("종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (ChronoUnit.MONTHS.between(startDate, endDate) > 3) {
            throw BusinessException.invalid("조회 기간은 최대 3개월까지만 가능합니다.");
        }
    }

    /**
     * 사용자 ID 유효성 체크
     * - 일반 사용자용 조회는 X-User-Id 헤더를 통해 userId가 주입되므로 필수 검증.
     */
    private void validateUserId(Long userId) {
        if (userId == null) {
            throw BusinessException.invalid("유저 ID는 필수입니다.");
        }
    }
}