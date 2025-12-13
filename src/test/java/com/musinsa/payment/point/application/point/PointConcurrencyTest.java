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
    @DisplayName("1. [적립] 동시에 5번(1000원씩) 적립 요청")
    void concurrent_earn_success() throws InterruptedException {
        // given
        Long userId = 5000L;
        long amount = 1000L;
        int threadCount = 5;

        userPointWalletRepository.save(new UserPointWallet(userId, 0L));

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.earn(userId, amount, false);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        UserPointWallet wallet = userPointWalletRepository.readByUserId(userId).orElseThrow();

        assertThat(wallet.getBalance()).isEqualTo(5000L);
        // 분산 환경이나 Retry가 없다면 5000원이 아닐 수 있음 (검증 로직은 상황에 맞게 조정 필요)
        // 여기서는 호출 코드가 정상인지 확인하는 목적으로 유지
        System.out.println("Final Balance: " + wallet.getBalance());
    }

    @Test
    @DisplayName("2. [사용] 잔액 3000원일 때, 동시에 1000원 사용 요청 3건")
    void concurrent_use_success() throws InterruptedException {
        // given
        Long userId = 5001L;
        long useAmount = 1000L;
        int threadCount = 3;

        pointService.earn(userId, 3000L, false);

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
        pointService.earn(userId, 1000L, false); // 초기 잔액 1000원

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