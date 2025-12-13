package com.musinsa.payment.point.application.point.service;

import com.musinsa.payment.point.domain.point.entity.*;
import com.musinsa.payment.point.domain.point.enums.PointStatus;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.domain.point.repository.*;
import com.musinsa.payment.point.global.error.BusinessException;
import com.musinsa.payment.point.global.policy.PointPolicyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointWalletRepository userPointWalletRepository;
    private final PointItemRepository pointItemRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PointPolicyManager policyManager;

    /**
     * 1. 포인트 적립
     */
    @Transactional
    public void earn(Long userId, long amount, boolean isManual) {
        // 1. 지갑 조회 (없으면 생성)
        UserPointWallet userPointWallet = userPointWalletRepository.findByUserId(userId)
                .orElseGet(() -> userPointWalletRepository.save(new UserPointWallet(userId, 0L)));

        // 2. 정책 검증
        if (amount < policyManager.getMinEarnAmount() || amount > policyManager.getMaxEarnAmount()) {
            throw BusinessException.invalid("적립 가능 금액 범위를 벗어났습니다.");
        }

        // 3. 지갑 잔액 증가 (내부에서 유효성 체크 [보유한도, -금액 체크])
        userPointWallet.earn(amount, policyManager.getMaxPossessionLimit());

        // 4. 아이템 생성 (저장)
        PointItem item = PointItem.builder()
                .userId(userId)
                .originalAmount(amount)
                .expireAt(LocalDateTime.now().plusDays(policyManager.getDefaultExpireDays()))
                .isManual(isManual)
                .build();
        pointItemRepository.save(item);

        // 5. 히스토리 생성 (Master)
        PointHistory history = PointHistory.builder()
                .userId(userId)
                .type(isManual ? PointType.ADMIN_GRANT : PointType.EARN)
                .amount(amount)
                .refId(String.valueOf(item.getId())) // 적립은 별도 주문번호가 없으므로 ItemID 등을 참조키로 사용
                .build();

        // 6. 상세 내역 연결 (Detail) - 적립된 아이템 연결
        history.addDetail(PointHistoryDetail.builder()
                .pointItem(item)
                .amount(amount)
                .build());

        pointHistoryRepository.save(history);
    }

    /**
     * 2. 적립 취소 (관리자 회수 등)
     * - 사용하지 않은 포인트에 한해 취소 가능
     */
    @Transactional
    public void cancelEarn(Long userId, Long pointItemId, boolean isManual) {
        UserPointWallet userPointWallet = userPointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> BusinessException.notFound("지갑을 찾을 수 없습니다."));

        PointItem item = pointItemRepository.findById(pointItemId)
                .orElseThrow(() -> BusinessException.notFound("적립 내역이 존재하지 않습니다."));

        if (!item.getUserId().equals(userId)) {
            throw BusinessException.invalid("해당 유저의 포인트가 아닙니다.");
        }

        // 1. 도메인 로직 호출 (이미 사용된 포인트인지 체크)
        item.cancelEarn();

        // 2. 지갑 잔액 차감
        userPointWallet.use(item.getOriginalAmount());

        // 3. 취소 히스토리 생성 (Master)
        PointHistory history = PointHistory.builder()
                .userId(userId)
                .type(isManual ? PointType.ADMIN_REVOKE : PointType.EARN_CANCEL)
                .amount(item.getOriginalAmount())
                .refId(String.valueOf(pointItemId)) // 기존 사용 포인트 ID 기록
                .build();

        // 4. 상세 내역 연결 (Detail)
        history.addDetail(PointHistoryDetail.builder()
                .pointItem(item)
                .amount(item.getOriginalAmount())
                .build());

        pointHistoryRepository.save(history);
    }

    /**
     * 3. 포인트 사용
     * - 우선순위: 관리자 지급(Manual) > 만료임박(ExpireAt ASC)
     * - 1개의 History에 N개의 Detail이 생성됨
     */
    @Transactional
    public void use(Long userId, long amount, String orderId) {
        // 1. 지갑 조회 (비관적 락)
        UserPointWallet userPointWallet = userPointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> BusinessException.notFound("지갑을 찾을 수 없습니다."));

        // 2. 중복 검사
        if (pointHistoryRepository.existsByUserIdAndRefIdAndType(userId, orderId, PointType.USE)) {
            throw BusinessException.invalid("이미 처리된 주문번호입니다.");
        }

        userPointWallet.use(amount);

        // 3. Master 히스토리 객체 생성
        PointHistory history = PointHistory.builder()
                .userId(userId)
                .type(PointType.USE)
                .amount(amount)
                .refId(orderId)
                .build();

        // 4. 차감할 아이템 조회
        // - 유효한(만료되지 않은) 포인트 중
        // - 관리자 수기지급 건을 우선적으로 사용하고 (Manual Desc)
        // - 그 중에서도 만료일이 임박한 순서대로 정렬 (ExpireAt Asc)
        List<PointItem> items = pointItemRepository.findByUserIdAndStatusAndExpireAtAfterOrderByIsManualDescExpireAtAsc(
                userId,
                PointStatus.AVAILABLE,
                LocalDateTime.now() // 현재 시간 넘겨줌
        );

        long remainToUse = amount;

        for (PointItem item : items) {
            if (remainToUse <= 0) break;

            long useAmount = Math.min(item.getRemainAmount(), remainToUse);

            // 5. 아이템 차감 (상태 변경)
            item.use(useAmount);

            // 6. Detail 추가 (Master에 귀속)
            history.addDetail(PointHistoryDetail.builder()
                    .pointItem(item)
                    .amount(useAmount)
                    .build());

            remainToUse -= useAmount;
        }

        // [중요] 만약 조회된 items가 없거나 부족하면 여기서 예외 발생
        // 즉, "지갑 잔액은 있어 보이는데, 실제 쓸 수 있는 살아있는 포인트가 부족한 상황" 방어
        if (remainToUse > 0) {
            // 여기서 예외가 터지면 트랜잭션 롤백 -> 지갑 잔액 차감도 취소됨 -> 데이터 정합성 유지
            throw BusinessException.invalid("유효한 포인트가 부족합니다. (만료된 포인트 포함됨)");
        }

        // 7. 통합 저장 (Cascade로 Detail까지 저장됨)
        pointHistoryRepository.save(history);
    }

    /**
     * 4. 포인트 사용 취소 (환불)
     * - 로직: 원본 History를 찾아 역순으로 상세 내역을 확인하며 복구합니다.
     * - 분기: 유효한 포인트는 '취소(USE_CANCEL)' 처리, 만료된 포인트는 '재적립(RESTORE)' 처리합니다.
     * - 검증: 이미 취소/재적립된 금액을 건너뛰고(Skip) 남은 금액에 대해서만 수행합니다.
     */
    @Transactional
    public void cancelUse(Long userId, String orderId, long cancelAmount) {
        // 1. 지갑 조회 (비관적 락으로 동시성 제어)
        UserPointWallet userPointWallet = userPointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> BusinessException.notFound("지갑을 찾을 수 없습니다."));

        // 2. 원본 사용 내역 조회 (상세내역 Fetch Join 필수)
        PointHistory originalHistory = pointHistoryRepository.findByUserIdAndRefIdAndTypeWithDetails(userId, orderId, PointType.USE)
                .orElseThrow(() -> BusinessException.notFound("해당 주문의 포인트 사용 이력이 없습니다."));

        // 3. 환불 가능 한도 검증
        // - 일반 취소(USE_CANCEL) + 만료 재적립(RESTORE) 모두 '이미 환불된 금액'으로 간주
        long totalPreviouslyRefunded = pointHistoryRepository.getSumAmountByUserIdAndRefIdAndTypes(userId, orderId, List.of(PointType.USE_CANCEL, PointType.RESTORE));

        if (originalHistory.getAmount() < totalPreviouslyRefunded + cancelAmount) {
            throw BusinessException.invalid("취소 가능한 금액을 초과했습니다.");
        }

        // 4. 복구 로직 수행을 위한 변수 준비
        // (Master 엔티티는 금액이 확정된 후 마지막에 생성하기 위해, Detail 리스트를 먼저 만듭니다)
        List<PointHistoryDetail> cancelDetails = new ArrayList<>();
        List<PointHistoryDetail> restoreDetails = new ArrayList<>();

        long currentCancelAmount = 0;  // 이번에 USE_CANCEL로 처리될 금액
        long currentRestoreAmount = 0; // 이번에 RESTORE로 처리될 금액

        long remainToCancel = cancelAmount;
        long skipAmount = totalPreviouslyRefunded; // 앞에서부터 이 금액만큼은 건너뜀

        // 5. 상세 내역 순회 (Skip & Rollback Logic)
        for (PointHistoryDetail detail : originalHistory.getDetails()) {
            if (remainToCancel <= 0) break;

            long usedAmount = detail.getAmount();

            // 현재 상세의 사용된 금액보다 이미 취소된 금액이 크면 스킵금액을 빼주고 넘어간다.
            if (skipAmount >= usedAmount) {
                skipAmount -= usedAmount;
                continue;
            }

            // 이번 상세 내역에서 환불할 금액 계산 (현재 상품금액 - 스킵금액)
            long availableRefund = usedAmount - skipAmount;
            // 이번에 요청된 환불금액(remainToCancel)과 이 상품의 남은 잔액(현재 상품금액 - 스킵금액) 중 작은 것을 선택
            long refundAmount = Math.min(availableRefund, remainToCancel);

            skipAmount = 0; // 스킵 금액 소진됨 (이제부터 환불 시작)

            PointItem originalItem = detail.getPointItem();

            // 포인트 만료 여부에 따라 처리 방식이 갈림
            if (originalItem.isExpired()) {
                // Case A: 포인트 만료됨 -> 신규 아이템 생성 (재적립)
                PointItem newItem = pointItemRepository.save(PointItem.builder()
                        .userId(userId)
                        .originalAmount(refundAmount)
                        .expireAt(LocalDateTime.now().plusDays(policyManager.getDefaultExpireDays())) // 정책에 따른 유효기간 부여
                        .isManual(false)
                        .build());

                // 재적립 상세 내역 추가 (원본 ID 추적 가능하게 저장)
                restoreDetails.add(PointHistoryDetail.builder()
                        .pointItem(newItem)
                        .amount(refundAmount)
                        .restoredFromItemId(originalItem.getId()) // [중요] 원본 ID 기록
                        .build());

                currentRestoreAmount += refundAmount;

            } else {
                // Case B: 포인트가 유효함 -> 원본 아이템 롤백 (잔액 복구)
                originalItem.cancel(refundAmount);

                // 취소 상세 내역 추가
                cancelDetails.add(PointHistoryDetail.builder()
                        .pointItem(originalItem)
                        .amount(refundAmount)
                        .restoredFromItemId(null) // 단순 롤백은 원본 추적 불필요 (본인이 원본)
                        .build());

                currentCancelAmount += refundAmount;
            }

            remainToCancel -= refundAmount;
        }

        // 6. 히스토리 저장 (금액이 존재하는 경우에만 Master 생성)

        // 6-1. 유효분 취소 히스토리 (USE_CANCEL)
        if (currentCancelAmount > 0) {
            PointHistory cancelHistory = PointHistory.builder()
                    .userId(userId)
                    .type(PointType.USE_CANCEL)
                    .amount(currentCancelAmount)
                    .refId(orderId)
                    .build();

            // Master-Detail 연결
            cancelDetails.forEach(cancelHistory::addDetail);
            pointHistoryRepository.save(cancelHistory);
        }

        // 6-2. 만료분 재적립 히스토리 (RESTORE)
        if (currentRestoreAmount > 0) {
            PointHistory restoreHistory = PointHistory.builder()
                    .userId(userId)
                    .type(PointType.RESTORE)
                    .amount(currentRestoreAmount)
                    .refId(orderId)
                    .build();

            restoreDetails.forEach(restoreHistory::addDetail);
            pointHistoryRepository.save(restoreHistory);
        }

        // 7. 지갑 총 잔액 복구
        userPointWallet.earn(cancelAmount, policyManager.getMaxPossessionLimit());
    }
}