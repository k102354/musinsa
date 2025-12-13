package com.musinsa.payment.point.application.point.service;

import com.musinsa.payment.point.domain.point.entity.PointItem;
import com.musinsa.payment.point.domain.point.entity.UserPointWallet;
import com.musinsa.payment.point.domain.point.enums.PointStatus;
import com.musinsa.payment.point.domain.point.repository.PointItemRepository; // [변경]
import com.musinsa.payment.point.domain.point.repository.UserPointWalletRepository; // [변경]
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PointScenarioTest {

    @Autowired private PointItemRepository pointItemRepository; // [변경] Ledger -> Item
    @Autowired private UserPointWalletRepository userPointWalletRepository;
    @Autowired
    private PointService pointService;

    @Test
    @DisplayName("관리자 수기지급 우선 사용 -> 부분 취소 시 만료일 늦은 순 복구 검증")
    //@Rollback(false) 데이터 확인 필요시 주석처리하여 데이터베이스 조회
    void complex_priority_check() {
        // given
        Long userId = 1234L;
        String orderId = "ORD-PRIORITY-TEST";

        // 0. 유저 포인트 지갑 생성 (총 잔액 3000원으로 세팅)
        // [변경] UserPointWallet 생성자 및 balance 필드 반영
        userPointWalletRepository.save(new UserPointWallet(userId, 3000L));

        // [데이터 세팅] 서로 다른 조건의 포인트 3가지 준비
        LocalDateTime now = LocalDateTime.now();

        // A: [일반] 만료 임박 (D+10) -> 사용 우선순위 2위
        PointItem itemA = pointItemRepository.save(PointItem.builder()
                .userId(userId).originalAmount(1000L).isManual(false)
                .expireAt(now.plusDays(10)).build());

        // B: [일반] 만료 넉넉 (D+100) -> 사용 우선순위 3위
        PointItem itemB = pointItemRepository.save(PointItem.builder()
                .userId(userId).originalAmount(1000L).isManual(false)
                .expireAt(now.plusDays(100)).build());

        // C: [수기] 만료 중간 (D+50) -> 사용 우선순위 1위 (관리자 지급 우선)
        PointItem itemC = pointItemRepository.save(PointItem.builder()
                .userId(userId).originalAmount(1000L).isManual(true)
                .expireAt(now.plusDays(50)).build());

        // 현재 상태: A(1000), B(1000), C(1000) = 총 3000원
        // 사용 예상 순서: C(수기) -> A(만료임박) -> B(만료넉넉)

        // when 1: 2000원 사용
        pointService.use(userId, 2000L, orderId);

        // then 1: 사용 로직 검증 (수기 -> 만료임박 순으로 빠졌는지)
        // [변경] ID 타입이 UUID여도 findById는 그대로 동작함
        PointItem resultC = pointItemRepository.findById(itemC.getId()).orElseThrow(); // 우선순위1
        PointItem resultA = pointItemRepository.findById(itemA.getId()).orElseThrow(); // 우선순위 2
        PointItem resultB = pointItemRepository.findById(itemB.getId()).orElseThrow(); // 우선순위 3

        // C(수기)는 다 썼어야 함 (잔액 0) (우선순위 1)
        assertThat(resultC.getRemainAmount()).isEqualTo(0L);
        // A(만료임박)도 다 썼어야 함 (잔액 0) (우선순위 2)
        assertThat(resultA.getRemainAmount()).isEqualTo(0L);
        // B(만료넉넉)는 하나도 안 쓰고 남았어야 함 (잔액 1000) (우선순위 3)
        assertThat(resultB.getRemainAmount()).isEqualTo(1000L);


        // =============================================================
        // 취소 시나리오 시작
        // 사용된 내역: C(수기, D+50), A(일반, D+10)
        // 취소 정렬 조건: 만료일 늦은 순(DESC) -> C(D+50)가 A(D+10)보다 먼저 나와야 함
        // =============================================================

        // when 2: 1500원 부분 취소
        pointService.cancelUse(userId, orderId, 1500L);

        // then 2: 취소 로직 검증
        // 1500원 복구 흐름 예상:
        // 1. C(D+50)가 먼저 조회됨 -> 1000원 전액 복구
        // 2. A(D+10)가 다음 조회됨 -> 500원 부분 복구

        PointItem finalC = pointItemRepository.findById(itemC.getId()).orElseThrow();
        PointItem finalA = pointItemRepository.findById(itemA.getId()).orElseThrow();

        // 검증: C는 1000원 썼다가 다시 1000원이 되어야 함 (전액 복구)
        assertThat(finalC.getRemainAmount()).isEqualTo(1000L);

        // 검증: A는 1000원 썼다가 500원만 복구되어 500원이 남아야 함 (부분 복구)
        assertThat(finalA.getRemainAmount()).isEqualTo(500L);

        // 검증: 총 잔액은 B(1000) + C(1000) + A(500) = 2500원
        UserPointWallet finalUserPointWallet = userPointWalletRepository.findById(userId).orElseThrow();
        assertThat(finalUserPointWallet.getBalance()).isEqualTo(2500L);
    }

    @Test
    @DisplayName("사용 후 취소 시점에 이미 만료된 포인트는 원복되지 않고 신규 포인트로 재적립된다")
    //@Rollback(false) 데이터 확인 필요시 주석처리하여 데이터베이스 조회
    void cancel_expired_point_should_create_new_point() {
        // given
        Long userId = 99992L; // 테스트용 별도 ID
        String orderId = "ORD-EXPIRE-TEST1234";
        LocalDateTime now = LocalDateTime.now();

        // 0. 지갑 생성 (초기 1000원)
        userPointWalletRepository.save(new UserPointWallet(userId, 1000L));

        // 1. 포인트 생성: 내일 만료되는 포인트 (유효기간이 아주 짧은 상황 가정)
        PointItem expiringItem = pointItemRepository.save(PointItem.builder()
                .userId(userId)
                .originalAmount(1000L)
                .isManual(false)
                .expireAt(now.plusDays(1)) // 내일 만료
                .build());

        // 2. 포인트 사용 (1000원 전액 사용)
        pointService.use(userId, 1000L, orderId);

        // 검증 1: 사용 직후 잔액 0원 확인
        PointItem usedItem = pointItemRepository.findById(expiringItem.getId()).orElseThrow();
        assertThat(usedItem.getRemainAmount()).isEqualTo(0L);

        // DB에 저장된 해당 포인트의 만료일을 '어제'로 강제 수정
        expiringItem.setExpired();;

        // when: 1000원 취소 요청
        pointService.cancelUse(userId, orderId, 1000L);

        // then: 검증 로직

        // 1. 원본 포인트(expiringItem)는 복구되지 않고 그대로 0원이어야 함 (만료되었으므로)
        PointItem originalResult = pointItemRepository.findById(expiringItem.getId()).orElseThrow();
        assertThat(originalResult.getRemainAmount())
                .as("만료된 원본 포인트는 잔액이 복구되면 안된다")
                .isEqualTo(0L);

        // 2. 대신 신규 포인트가 1건 생성되어 있어야 함
        // (userId로 조회했을 때, 원본 ID가 아닌 새로운 ID의 포인트가 존재하는지 확인)
        PointItem newItem = pointItemRepository.findByUserIdAndStatusAndExpireAtAfterOrderByIsManualDescExpireAtAsc(userId, PointStatus.AVAILABLE, LocalDateTime.now()).stream()
                .filter(item -> !item.getId().equals(expiringItem.getId())) // 원본 제외
                .findFirst()
                .orElseThrow(() -> new AssertionError("신규 적립된 포인트가 없습니다."));

        // 3. 신규 포인트 검증
        assertThat(newItem.getOriginalAmount()).isEqualTo(1000L);
        assertThat(newItem.getRemainAmount()).isEqualTo(1000L);
        assertThat(newItem.getExpireAt()).isAfter(now); // 신규 포인트는 유효기간이 넉넉해야 함

        // 4. 유저 지갑 총 잔액 검증 (다시 1000원이 되어야 함)
        UserPointWallet userWallet = userPointWalletRepository.findById(userId).orElseThrow();
        assertThat(userWallet.getBalance()).isEqualTo(1000L);
    }
}