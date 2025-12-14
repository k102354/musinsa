package com.musinsa.payment.point.application.point;

import com.musinsa.payment.point.application.point.service.PointService;
import com.musinsa.payment.point.domain.point.entity.UserPointWallet;
import com.musinsa.payment.point.domain.point.repository.UserPointWalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PointIntegrationTest {

    @Autowired
    private PointService pointService; // [변경] Facade 대신 Service 주입

    @Autowired
    private UserPointWalletRepository userPointWalletRepository;

    @Test
    @DisplayName("적립 -> 사용 -> 부분 취소 -> 잔액 확인")
    //@Rollback(false) 데이터 확인 필요시 주석처리하여 데이터베이스 조회
    void full_scenario_test() {
        // given
        Long userId = 999L;
        String orderId = "ORD-TEST-001";

        // 1. 10,000원 적립
        pointService.earn(userId, 10000L, false, orderId);

        // 2. 3,000원 사용
        pointService.use(userId, 3000L, orderId);

        // 3. 1,000원 부분 취소
        pointService.cancelUse(userId, orderId, 1000L);

        // when
        UserPointWallet userPointWallet = userPointWalletRepository.findByUserId(userId).orElseThrow();

        // then
        // 예상 잔액: 10000 - 3000 + 1000 = 8000원
        assertThat(userPointWallet.getBalance()).isEqualTo(8000L);
    }
}