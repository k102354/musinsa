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

/**
 * 포인트 Command Service
 * - 역할: 포인트의 적립, 사용, 취소 등 "상태 변화(쓰기) 트랜잭션"을 관리.
 * - 특징: UserPointWallet의 동시성 제어를 위해 Lock을 사용하며, 모든 작업은 원자성(Atomicity)을 보장해야 함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointWalletRepository userPointWalletRepository;
    private final PointItemRepository pointItemRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PointPolicyManager policyManager;

    /**
     * 1. 포인트 적립 (EARN, ADMIN_GRANT)
     * - 트랜잭션: 하나의 트랜잭션으로 Wallet, Item, History 모두 저장/업데이트
     */
    @Transactional
    public void earn(Long userId, long amount, boolean isManual, String refId) {

        // 1. 지갑조회(Lock)
        UserPointWallet userPointWallet = userPointWalletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    // 유저가 없는 신규 생성의 경우, 락을 걸 수 없으므로 바로 저장
                    // (단, 이 경우에도 Unique Constraint가 없다면 동시 생성 문제가 있을 수 있으나,
                    // 보통 회원 가입 후 적립이므로 지갑은 존재한다고 가정)
                    return userPointWalletRepository.save(new UserPointWallet(userId, 0L));
                });

        // 2. 중복적립 체크
        PointType type = isManual ? PointType.ADMIN_GRANT : PointType.EARN;
        if (pointHistoryRepository.existsByUserIdAndRefIdAndType(userId, refId, type)) {
            throw BusinessException.invalid("이미 처리된 적립 요청입니다.");
        }

        // 3. 정책 검증
        if (amount < policyManager.getMinEarnAmount() || amount > policyManager.getMaxEarnAmount()) {
            throw BusinessException.invalid("적립 가능 금액 범위를 벗어났습니다.");
        }

        // 4. 지갑 잔액 증가 (내부에서 보유 한도 초과 체크)
        userPointWallet.earn(amount, policyManager.getMaxPossessionLimit());

        // 5. 아이템 생성: 적립 정책에 따른 유효 기간 부여
        PointItem item = PointItem.builder()
                .userId(userId)
                .originalAmount(amount)
                .expireAt(LocalDateTime.now().plusDays(policyManager.getDefaultExpireDays()))
                .isManual(isManual)
                .build();
        pointItemRepository.save(item);

        // 6. 히스토리 생성 (Master)
        PointHistory history = PointHistory.builder()
                .userId(userId)
                .type(isManual ? PointType.ADMIN_GRANT : PointType.EARN)
                .amount(amount)
                // 적립의 참조키는 해당 PointItem ID를 사용 (이벤트/주문이 없는 경우)
                .refId(String.valueOf(refId))
                .build();

        //7. 상세 내역 연결 (Detail) - 적립된 아이템과 1:1 연결
        history.addDetail(PointHistoryDetail.builder()
                .pointItem(item)
                .amount(amount)
                .build());

        // History 저장 시 Cascade 옵션으로 Detail까지 함께 저장됨
        pointHistoryRepository.save(history);
    }

    /**
     * 2. 적립 취소 (EARN_CANCEL / ADMIN_REVOKE)
     * - 트랜잭션: UserPointWallet에 PESSIMISTIC_WRITE Lock 적용하여 동시성 제어
     * - 제약: 원본 PointItem이 "전액 미사용 상태"일 때만 가능하도록 Item 도메인 로직에 위임
     */
    @Transactional
    public void cancelEarn(Long userId, Long pointItemId, boolean isManual) {
        // 1. 지갑 조회 (Lock)
        UserPointWallet userPointWallet = userPointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> BusinessException.notFound("지갑을 찾을 수 없습니다."));

        PointItem item = pointItemRepository.findById(pointItemId)
                .orElseThrow(() -> BusinessException.notFound("적립 내역이 존재하지 않습니다."));

        if (!item.getUserId().equals(userId)) {
            throw BusinessException.invalid("해당 유저의 포인트가 아닙니다.");
        }

        // 2. PointItem 도메인 로직 호출 (여기서 잔액/상태 체크 및 CANCELED 처리)
        item.cancelEarn();

        // 3. 지갑 잔액 차감 (원본 금액만큼 차감)
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
     * 3. 포인트 사용 (USE)
     * - 트랜잭션: Wallet, Item의 잔액 업데이트 및 History 저장이 원자적으로 수행됨
     * - 핵심 로직: PointItem 차감 우선순위 적용 (Manual DESC, ExpireAt ASC)
     */
    @Transactional
    public void use(Long userId, long amount, String refId) {
        // 1. 지갑 조회 (비관적 락으로 동시성 제어)
        UserPointWallet userPointWallet = userPointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> BusinessException.notFound("지갑을 찾을 수 없습니다."));

        // 2. 중복 검사 (멱등성 확보): 해당 주문번호로 이미 USE 트랜잭션이 발생했는지 체크
        if (pointHistoryRepository.existsByUserIdAndRefIdAndType(userId, refId, PointType.USE)) {
            throw BusinessException.invalid("이미 처리된 주문번호입니다.");
        }

        // 3. 지갑 잔액 선 차감
        userPointWallet.use(amount);

        // 4. Master 히스토리 객체 생성 (저장전)
        PointHistory history = PointHistory.builder()
                .userId(userId)
                .type(PointType.USE)
                .amount(amount)
                .refId(refId)
                .build();

        // 5. 차감할 아이템 조회
        List<PointItem> items = pointItemRepository.findByUserIdAndStatusAndExpireAtAfterOrderByIsManualDescExpireAtAsc(
                userId,
                PointStatus.AVAILABLE,
                LocalDateTime.now() // 만료되지 않은 포인트만 조회
        );

        long remainToUse = amount;

        // 6. PointItem 잔액 소진 및 Detail 생성
        for (PointItem item : items) {
            if (remainToUse <= 0) break; // 사용 금액을 모두 채웠다면 루프 종료

            long useAmount = Math.min(item.getRemainAmount(), remainToUse);

            // 6-1. 아이템 차감 (상태 변경 로직은 Item 도메인에 위임)
            item.use(useAmount);

            // 6-2. Detail 추가: 어떤 Item을 얼마만큼 썼는지 기록
            history.addDetail(PointHistoryDetail.builder()
                    .pointItem(item)
                    .amount(useAmount)
                    .build());

            remainToUse -= useAmount;
        }

        // 7. 포인트 부족 체크 (지갑 잔액은 속일 수 있지만, 유효한 Item은 부족할 수 있음)
        if (remainToUse > 0) {
            // 예외 발생 시 트랜잭션 롤백 -> 3단계의 지갑 잔액 차감도 자동 취소되어 정합성 유지
            throw BusinessException.invalid("유효한 포인트가 부족합니다. (만료된 포인트 포함됨)");
        }

        // 8. 통합 저장 (Detail까지 Cascade로 저장)
        pointHistoryRepository.save(history);
    }

    /**
     * 4. 포인트 사용 취소 (USE_CANCEL / RESTORE)
     * - 트랜잭션: Wallet, Item, History 모두 처리
     * - 핵심 로직: 원본 사용 내역을 역순으로 따라가며 복구(Rollback) 수행.
     * - 만료 포인트 처리: 유효한 포인트는 Item 잔액 롤백(USE_CANCEL), 만료된 포인트는 신규 생성(RESTORE)으로 분기
     */
    @Transactional
    public void cancelUse(Long userId, String orderId, long cancelAmount) {
        // 1. 지갑 조회 (Lock)
        UserPointWallet userPointWallet = userPointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> BusinessException.notFound("지갑을 찾을 수 없습니다."));

        // 2. 원본 사용 내역 조회 (Fetch Join으로 Detail까지 함께 로딩하여 N+1 방지)
        PointHistory originalHistory = pointHistoryRepository.findByUserIdAndRefIdAndTypeWithDetails(userId, orderId, PointType.USE)
                .orElseThrow(() -> BusinessException.notFound("해당 주문의 포인트 사용 이력이 없습니다."));

        // 3. 환불 가능 한도 검증 (부분 취소/재취소 방어)
        // - 이미 USE_CANCEL이나 RESTORE된 금액을 합산하여, 요청된 취소 금액이 원본 금액을 초과하는지 체크
        long totalPreviouslyRefunded = pointHistoryRepository.getSumAmountByUserIdAndRefIdAndTypes(userId, orderId, List.of(PointType.USE_CANCEL, PointType.RESTORE));

        if (originalHistory.getAmount() < totalPreviouslyRefunded + cancelAmount) {
            throw BusinessException.invalid("취소 가능한 금액을 초과했습니다.");
        }

        // 4. 복구 로직 수행을 위한 변수 준비
        List<PointHistoryDetail> cancelDetails = new ArrayList<>();
        List<PointHistoryDetail> restoreDetails = new ArrayList<>();

        long currentCancelAmount = 0;  // USE_CANCEL (유효분 롤백) 합계
        long currentRestoreAmount = 0; // RESTORE (만료분 신규 적립) 합계

        long remainToCancel = cancelAmount;
        long skipAmount = totalPreviouslyRefunded; // 이미 취소된 금액만큼 원본 Detail을 건너뛰기 위한 변수

        // 5. 상세 내역 순회 (Rollback Logic)
        for (PointHistoryDetail detail : originalHistory.getDetails()) {
            if (remainToCancel <= 0) break;

            long usedAmount = detail.getAmount();

            // 5-1. Skip 처리: 이미 취소된 금액(skipAmount)만큼 해당 detail 사용액을 건너뜀
            if (skipAmount >= usedAmount) {
                skipAmount -= usedAmount;
                continue;
            }

            // 5-2. 실제 환불 금액 계산
            long availableRefund = usedAmount - skipAmount; // 이 detail에서 아직 환불되지 않은 금액
            long refundAmount = Math.min(availableRefund, remainToCancel); // 이번에 환불해야 할 금액

            skipAmount = 0; // 스킵 금액 소진됨 (이제부터 환불 시작)

            PointItem originalItem = detail.getPointItem();

            // 5-3. 만료 여부에 따른 분기 처리
            if (originalItem.isExpired()) {
                // Case A: 만료됨 -> 신규 Item 생성 (재적립: RESTORE)
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
                        .restoredFromItemId(originalItem.getId()) // 원본 Item ID 기록 (추적용)
                        .build());

                currentRestoreAmount += refundAmount;

            } else {
                // Case B: 유효함 -> 원본 Item 잔액 복구 (취소: USE_CANCEL)
                originalItem.cancel(refundAmount);

                cancelDetails.add(PointHistoryDetail.builder()
                        .pointItem(originalItem)
                        .amount(refundAmount)
                        .restoredFromItemId(null) // 단순 롤백은 원본 추적 불필요 (본인이 원본)
                        .build());

                currentCancelAmount += refundAmount; // 남은 취소 금액 갱신
            }

            remainToCancel -= refundAmount;
        }

        // 6. 히스토리 저장 (Master 생성)
        // 금액이 0보다 큰 경우에만 해당 타입의 History를 저장 (부분 취소 처리)
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

        // 7. 지갑 총 잔액 복구 (USE_CANCEL + RESTORE 합계 = cancelAmount)
        userPointWallet.earn(cancelAmount, policyManager.getMaxPossessionLimit());
    }
}