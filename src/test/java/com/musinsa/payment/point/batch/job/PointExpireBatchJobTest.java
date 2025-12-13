package com.musinsa.payment.point.batch.job;

import com.musinsa.payment.point.batch.job.PointExpireBatchConfig; // 배치 설정 Import
import com.musinsa.payment.point.domain.point.entity.*;
import com.musinsa.payment.point.domain.point.enums.PointStatus;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.domain.point.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest // 통합 테스트 환경 로드
@SpringBatchTest // 배치 테스트 유틸리티 로드
//@ActiveProfiles("test") // application-test.yml 사용 시
class PointExpireBatchJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils; // 잡 실행을 도와주는 유틸

    @Autowired private PointItemRepository pointItemRepository;
    @Autowired private UserPointWalletRepository userPointWalletRepository;
    @Autowired private PointHistoryRepository pointHistoryRepository;

    @AfterEach
    void tearDown() {
        // 테스트 간 데이터 간섭 방지를 위해 정리
        pointHistoryRepository.deleteAll();
        pointItemRepository.deleteAll();
        userPointWalletRepository.deleteAll();
    }

    @Test
    @DisplayName("만료 배치 실행 시 유효기간이 지난 포인트는 만료 처리되고 지갑에서 차감된다")
    void pointExpireJob_test() throws Exception {
        // given
        Long userId = 4444L;
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);

        // 1. 지갑 생성 (잔액 3000원)
        userPointWalletRepository.save(new UserPointWallet(userId, 3000L));

        // 2. 포인트 아이템 생성
        // Item A: 1000원, 어제 만료됨 -> (만료 대상 O)
        PointItem expiredItem1 = PointItem.builder()
                .userId(userId)
                .originalAmount(1000L)
                .expireAt(tomorrow)
                .isManual(false)
                .build();
        expiredItem1.setExpired();
        pointItemRepository.save(expiredItem1);

        // Item B: 1000원, 어제 만료됨 -> (만료 대상 O)
        PointItem expiredItem2 = PointItem.builder()
                .userId(userId)
                .originalAmount(1000L)
                .expireAt(tomorrow)
                .isManual(false)
                .build();
        expiredItem2.setExpired();
        pointItemRepository.save(expiredItem2);

        // Item C: 1000원, 내일 만료됨 -> (만료 대상 X)
        PointItem validItem = pointItemRepository.save(PointItem.builder()
                .userId(userId)
                .originalAmount(1000L)
                .expireAt(tomorrow)
                .isManual(false)
                .build());

        // 배치 파라미터 (오늘 날짜 기준 실행)
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", LocalDate.now().toString())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // when
        // 배치 Job 실행!
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // 테스트 오류시 디버깅을 위한 오류 출력 코드
        /*if (jobExecution.getStatus() == BatchStatus.FAILED) {
            System.out.println("=== 배치 실패 원인 ===");
            // 모든 예외 리스트 출력
            jobExecution.getAllFailureExceptions().forEach(e -> e.printStackTrace());
        }*/

        // then
        // 1. 배치 상태 확인
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 2. 포인트 아이템 상태 검증
        PointItem findItem1 = pointItemRepository.findById(expiredItem1.getId()).get();
        PointItem findItem2 = pointItemRepository.findById(expiredItem2.getId()).get();
        PointItem findItem3 = pointItemRepository.findById(validItem.getId()).get();

        // 만료 대상은 EXPIRED 상태여야 함
        assertThat(findItem1.getStatus()).isEqualTo(PointStatus.EXPIRED);
        assertThat(findItem1.getRemainAmount()).isEqualTo(0L);

        assertThat(findItem2.getStatus()).isEqualTo(PointStatus.EXPIRED);
        assertThat(findItem2.getRemainAmount()).isEqualTo(0L);

        // 유효 대상은 그대로 AVAILABLE이어야 함
        assertThat(findItem3.getStatus()).isEqualTo(PointStatus.AVAILABLE);
        assertThat(findItem3.getRemainAmount()).isEqualTo(1000L);

        // 3. 지갑 잔액 검증
        // 3000원 - 2000원(만료) = 1000원 남아야 함
        UserPointWallet wallet = userPointWalletRepository.findByUserId(userId).get();
        assertThat(wallet.getBalance()).isEqualTo(1000L);

        // 4. 히스토리 검증
        // 2000원짜리 만료 2건 생겨야 함
        // Item별로 History를 생성하고 저장하므로, 2개의 History가 생겨야 함.
        List<PointHistory> histories = pointHistoryRepository.findAll();
        assertThat(histories).hasSize(2);

        assertThat(histories.get(0).getType()).isEqualTo(PointType.EXPIRE);
        assertThat(histories.get(0).getRefId()).contains("BATCH_"); // 배치 실행 기록
    }
}