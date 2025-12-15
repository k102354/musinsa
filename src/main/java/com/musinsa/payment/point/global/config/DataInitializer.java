package com.musinsa.payment.point.global.config;

import com.musinsa.payment.point.application.point.service.PointService;
import com.musinsa.payment.point.domain.point.entity.PointItem;
import com.musinsa.payment.point.domain.point.repository.PointItemRepository;
import com.musinsa.payment.point.domain.point.repository.UserPointWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("local") // 로컬 개발 환경에서만 동작하도록 설정 (운영 환경 방지)
public class DataInitializer {

    private final PointService pointService;
    private final UserPointWalletRepository userPointWalletRepository;
    private final PointItemRepository pointItemRepository; // [추가] 만료일 조작을 위해 필요

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            // 이미 데이터가 있다면 초기화 스킵 (재실행 시 중복 방지)
            if (userPointWalletRepository.count() > 0) {
                log.info("이미 데이터가 존재하여 초기화를 건너뜁니다.");
                return;
            }

            log.info("예시 데이터 생성을 시작합니다...");

            // 1. 신규 회원 가입 및 웰컴 포인트 지급 (유저 10명)
            for (long userId = 1; userId <= 10; userId++) {
                String eventId = "WELCOME_EVENT_" + userId;
                // 3000원 수기 지급 (Manual=true)
                pointService.earn(userId, 3000L, true, eventId);
            }
            log.info("신규 회원 10명 웰컴 포인트 지급 완료");

            // 2. 상품 주문 시뮬레이션 (랜덤 주문)
            Random random = new Random();
            for (int i = 0; i < 20; i++) {
                long userId = random.nextLong(1, 11); // 1~10번 유저 중 랜덤
                long amount = (random.nextLong(1, 4) * 500); // 500, 1000, 1500원 중 랜덤
                String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

                try {
                    pointService.use(userId, amount, orderId);
                } catch (Exception e) {
                    // 잔액 부족 등으로 실패할 수 있음 (자연스러운 현상)
                    log.debug("주문 실패 (잔액 부족 등): User={}, Amt={}", userId, amount);
                }
            }
            log.info("상품 주문 20건 시뮬레이션 완료");

            // 3. 고객 변심으로 인한 환불 (부분 취소)
            // 1번 유저
            long targetUser = 1L;
            String refundOrderId = "ORD-REFUND-TEST";

            // A. 적립
            pointService.earn(targetUser, 5000L, false, "EARN-FOR-REFUND");
            // B. 사용 (3000원)
            pointService.use(targetUser, 3000L, refundOrderId);
            // C. 부분 취소 (1000원 환불)
            pointService.cancelUse(targetUser, refundOrderId, 1000L);

            log.info("1번 유저 환불(부분 취소) 시나리오 완료");

            // =========================================================
            // 4.만료된 포인트 재적립(부활) 시나리오 - 2번 유저
            // =========================================================
            long expiredUser = 2L;
            String expiredOrderId = "ORD-REFUND-EXPIRED";

            // A. 적립 (일반 적립)
            pointService.earn(expiredUser, 2000L, false, "EARN-FOR-EXPIRED-TEST");

            // B. 사용 (전액 사용)
            pointService.use(expiredUser, 2000L, expiredOrderId);

            // C.강제 만료 처리
            List<PointItem> items = pointItemRepository.findByUserId(expiredUser);
            for (PointItem item : items) {
                // PointItem 엔티티에 있는 테스트용 setExpired() 메서드 활용 (expireAt을 어제로 설정)
                if (item.getRemainAmount() == 0 && !item.isExpired()) { // 이미 사용된 것만 만료시킴
                    item.setExpired();
                    pointItemRepository.save(item);
                }
            }
            log.info("2번 유저의 사용 포인트를 강제로 만료 처리");

            // D. 취소 (환불) 요청
            // 시스템은 원본이 만료되었음을 감지하고 'RESTORE' 타입으로 신규 적립해야 합니다.
            pointService.cancelUse(expiredUser, expiredOrderId, 2000L);

            log.info(" 2번 유저 만료된 포인트 환불(재적립) 시나리오 완료");

            log.info("모든 예시 데이터 생성이 완료되었습니다.");
        };
    }
}