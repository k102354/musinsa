package com.musinsa.payment.point.application.point;

import com.musinsa.payment.point.application.point.service.PointService;
import com.musinsa.payment.point.domain.point.entity.UserPointWallet;
import com.musinsa.payment.point.domain.point.repository.PointHistoryRepository;
import com.musinsa.payment.point.domain.point.repository.PointItemRepository;
import com.musinsa.payment.point.domain.point.repository.UserPointWalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PointConcurrencyTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private UserPointWalletRepository userPointWalletRepository;
    @Autowired
    private PointItemRepository pointItemRepository;
    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAll();
        pointItemRepository.deleteAll();
        userPointWalletRepository.deleteAll();
    }

    @Test
    @DisplayName("1. [적립] 동일한 주문번호로 동시에 5번 적립 요청 시 1번만 성공해야 한다 (중복 방어)")
    void concurrent_earn_duplicate_prevention() throws InterruptedException {
        // given
        Long userId = 5000L;
        long amount = 1000L;
        int threadCount = 5;
        String refId = "DUPLICATE_ORDER_ID"; // [핵심] 동일한 주문번호 사용

        userPointWalletRepository.save(new UserPointWallet(userId, 0L));

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 동일한 refId로 동시에 요청
                    pointService.earn(userId, amount, false, refId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // "이미 처리된 적립 요청입니다" 예외 발생 예상
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        UserPointWallet wallet = userPointWalletRepository.readByUserId(userId).orElseThrow();

        // 검증 1: 성공 횟수는 1회여야 함
        assertThat(successCount.get()).isEqualTo(1);

        // 검증 2: 실패 횟수는 4회여야 함
        assertThat(failCount.get()).isEqualTo(4);

        // 검증 3: 잔액은 1번만 적립된 1000원이어야 함 (5000원이면 실패)
        assertThat(wallet.getBalance()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("2. [사용] 잔액 3000원일 때, 동시에 1000원 사용 요청 3건")
    void concurrent_use_success() throws InterruptedException {
        // given
        Long userId = 5001L;
        long useAmount = 1000L;
        int threadCount = 3;
        String refId = "ORD_00001";

        pointService.earn(userId, 3000L, false, refId);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            int orderIndex = i;
            executorService.submit(() -> {
                try {
                    // DTO 대신 파라미터 전달
                    pointService.use(userId, useAmount, "ORDER_UUID_" + orderIndex);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("Use Failed: " + e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        UserPointWallet wallet = userPointWalletRepository.readByUserId(userId).orElseThrow();

        // 동시성 제어가 완벽하다면 0원이어야 함
        assertThat(wallet.getBalance()).isEqualTo(0L);
        assertThat(successCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("동일한 주문번호 3번 사용 요청 시 1번만 성공하고 잔액은 0원이어야 한다")
    void duplicate_order_use() throws InterruptedException {
        // given
        Long userId = 1L;
        pointService.earn(userId, 1000L, false, "ORD_TEST001"); // 초기 잔액 1000원

        int threadCount = 3;
        // 멀티스레드 이용을 위한 ExecutorService (비동기 작업 실행)
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        // 모든 스레드의 작업이 끝날 때까지 기다리기 위한 Latch
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.use(userId, 1000L, "ORDER_UUID_002");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 잔액 부족 등 예외 발생 시 실패 카운트 증가
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown(); // 작업 완료 알림
                }
            });
        }

        latch.await(); // 모든 스레드가 끝날 때까지 대기

        // then
        UserPointWallet wallet = userPointWalletRepository.readByUserId(userId).orElseThrow();

        // 검증: 성공은 딱 1번, 나머지는 실패해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(2);

        // 검증: 잔액은 0원이어야 함 (-1000, -2000 되면 안됨)
        assertThat(wallet.getBalance()).isEqualTo(0L);
    }

}