package com.musinsa.payment.point.application.point.service;

import com.musinsa.payment.point.domain.point.entity.*;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.domain.point.repository.*;
import com.musinsa.payment.point.global.policy.PointPolicyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @InjectMocks
    private PointService pointService;

    // [변경] PointUsageRepository 삭제됨
    @Mock private UserPointWalletRepository userPointWalletRepository;
    @Mock private PointHistoryRepository pointHistoryRepository;
    @Mock private PointItemRepository pointItemRepository;
    @Mock private PointPolicyManager policyManager;

    @Test
    @DisplayName("부분 취소 시 기 취소된 금액은 건너뛰고(Skip) 남은 금액만 환불되어야 한다")
    //@Rollback(false) 데이터 확인 필요시 주석처리하여 데이터베이스 조회
    void cancelUse_with_skip_logic() {
        // given
        Long userId = 1L;
        String orderId = "ORD-001";
        long cancelRequestAmount = 1500L;

        // 1. Mock Wallet (비관적 락 조회 가정)
        UserPointWallet userPointWallet = new UserPointWallet(userId, 0L);
        given(userPointWalletRepository.findByUserIdForUpdate(userId))
                .willReturn(Optional.of(userPointWallet));

        // 2. 포인트 아이템 준비 (A, B, C) - 각각 1000원씩
        // [Spy 사용] rollback 호출 여부 검증을 위해 spy 객체 생성
        PointItem itemA = spy(createItem(1000L));
        PointItem itemB = spy(createItem(1000L));
        PointItem itemC = spy(createItem(1000L));

        // [중요] 사용된 상태를 모사하기 위해 전액 차감 (잔액 0원 상태로 시작)
        itemA.use(1000L);
        itemB.use(1000L);
        itemC.use(1000L);

        // 3. 원본 사용 이력 (Master) - 총 3000원 사용
        PointHistory originalHistory = PointHistory.builder()
                .userId(userId)
                .type(PointType.USE)
                .amount(3000L)
                .refId(orderId)
                .build();

        // 4. 상세 내역 연결 (Detail) - 순서가 중요! (Skip 로직 테스트)
        // 가정: Repository에서 조회 시 [C, B, A] 순서로 담겨있다고 가정
        // (보통 insert 순서나 정렬 조건에 따라 다르지만, 여기서는 리스트 순서대로 로직이 동작함)
        originalHistory.addDetail(PointHistoryDetail.builder().pointItem(itemC).amount(1000L).build());
        originalHistory.addDetail(PointHistoryDetail.builder().pointItem(itemB).amount(1000L).build());
        originalHistory.addDetail(PointHistoryDetail.builder().pointItem(itemA).amount(1000L).build());

        // [변경] findByRefIdAndTypeWithDetails 호출 시 위에서 만든 history 리턴
        given(pointHistoryRepository.findByUserIdAndRefIdAndTypeWithDetails(userId, orderId, PointType.USE))
                .willReturn(Optional.of(originalHistory));

        // 5. [핵심] 이미 1500원이 취소된 상태 (기 취소액)
        // -> C(1000) 전액과 B(500) 만큼이 이미 취소되었다고 가정
        // [수정] 모든 인자를 Matcher 형태로 통일해야 함 (eq 사용)
        given(pointHistoryRepository.getSumAmountByUserIdAndRefIdAndTypes(userId, eq(orderId), anyList()))
                .willReturn(1500L);

        // Policy Mock
        given(policyManager.getMaxPossessionLimit()).willReturn(100000L);

        // when
        pointService.cancelUse(userId, orderId, cancelRequestAmount);

        // then
        // ===============================================================
        // [검증 시나리오]
        // 요청 취소액: 1500원
        // 기 취소액(Skip): 1500원
        // ---------------------------------------------------------------
        // 1. Item C (1000원):
        //    - Skip 잔액 1500 >= 1000 -> 전액 Skip.
        //    - rollback() 호출되면 안 됨.
        //
        // 2. Item B (1000원):
        //    - Skip 잔액 500 남음.
        //    - 1000 중 500은 Skip, 나머지 500은 환불 가능.
        //    - 요청액(1500) 중 500원 사용하여 환불 처리.
        //    - rollback(500) 호출되어야 함.
        //
        // 3. Item A (1000원):
        //    - Skip 잔액 0.
        //    - 요청액 잔액 1000 남음.
        //    - rollback(1000) 호출되어야 함.
        // ===============================================================

        verify(itemC, never()).cancel(anyLong()); // C는 건드리지 않음
        verify(itemB, times(1)).cancel(500L);     // B는 500원만 복구
        verify(itemA, times(1)).cancel(1000L);    // A는 1000원 전액 복구

        // Wallet 잔액 검증: 0원에서 1500원 증가해야 함
        assertThat(userPointWallet.getBalance()).isEqualTo(1500L);
    }

    // Helper Method
    private PointItem createItem(long amount) {
        return PointItem.builder()
                .userId(1L)
                .originalAmount(amount)
                .expireAt(LocalDateTime.now().plusDays(365)) // 유효기간 넉넉히
                .isManual(false)
                .build();
    }
}