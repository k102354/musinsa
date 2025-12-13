package com.musinsa.payment.point.batch.job;

import com.musinsa.payment.point.domain.point.entity.*;
import com.musinsa.payment.point.domain.point.enums.PointStatus;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.domain.point.repository.*;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaCursorItemReader; // 구체적 타입 import
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 포인트 만료 배치 Job 설정 (PointExpireJob)
 * - 유효기간이 만료된 PointItem의 잔액을 0으로 만들고 상태를 EXPIRED로 변경하며,
 * UserPointWallet과 PointHistory에 해당 차감 내역을 기록하여 데이터 정합성을 유지함.
 * - 전략: JpaCursorItemReader를 사용하여 대용량 데이터 조회 시 메모리 부하를 줄임.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PointExpireBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;

    private final UserPointWalletRepository userPointWalletRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PointItemRepository pointItemRepository;

    private static final int CHUNK_SIZE = 1000;

    @Bean
    public Job pointExpireJob() {
        return new JobBuilder("pointExpireJob", jobRepository)
                .start(pointExpireStep())
                .build();
    }

    @Bean
    public Step pointExpireStep() {
        return new StepBuilder("pointExpireStep", jobRepository)
                .<PointItem, PointItem>chunk(CHUNK_SIZE, transactionManager)
                .reader(pointExpireItemReader(null))
                // .processor()는 제거합니다. (Writer에서 처리)
                .writer(pointExpireItemWriter())
                .build();
    }

    /**
     * [Reader] 만료 대상 PointItem 조회
     * - JpaCursorItemReader 사용: Page 방식보다 메모리 효율적이며, 대용량 처리에 유리함.
     * - @StepScope: Job Parameter(targetDate)를 받기 위해 Step 실행 시점에 빈이 생성됨.
     */
    @Bean
    @StepScope
    public JpaCursorItemReader<PointItem> pointExpireItemReader(@Value("#{jobParameters[targetDate]}") String targetDate) {
        // 기준일 설정 (파라미터가 없으면 현재 시간 기준)
        LocalDateTime criterion = (targetDate != null)
                ? LocalDate.parse(targetDate).atStartOfDay()
                : LocalDateTime.now();

        log.info("Batch Reader Start: criterion={}", criterion);

        return new JpaCursorItemReaderBuilder<PointItem>()
                .name("pointExpireItemReader")
                .entityManagerFactory(entityManagerFactory)
                // 쿼리: 상태 AVAILABLE && 만료일(expireAt)이 기준일 이전인 Item 조회
                .queryString("SELECT p FROM PointItem p WHERE p.status = :status AND p.expireAt < :date")
                .parameterValues(Map.of(
                        "status", PointStatus.AVAILABLE,
                        "date", criterion
                ))
                .build();
    }

    /**
     * [Writer] 만료 처리 및 DB 업데이트
     * - 역할: 1. Item 상태 변경(expire), 2. History 생성, 3. Wallet 잔액 차감.
     * - 모든 DB 쓰기 작업은 이 Writer의 청크 단위 트랜잭션 내에서 수행됨.
     */
    @Bean
    public ItemWriter<PointItem> pointExpireItemWriter() {
        return chunk -> {
            List<? extends PointItem> items = chunk.getItems();
            List<PointHistory> histories = new ArrayList<>();

            // 1. 개별 아이템 만료 처리 및 히스토리 생성
            for (PointItem item : items) {
                // A. 만료 전 잔액 스냅샷
                long expireAmount = item.getRemainAmount();

                if (expireAmount <= 0) continue; // 잔액이 0이면 스킵

                // A. Item 상태 변경 (remainAmount=0, status=EXPIRED)
                item.expire();

                // B. EXPIRE PointHistory 객체 생성 (이유: 만료 내역 추적)
                PointHistory history = PointHistory.builder()
                        .userId(item.getUserId())
                        .type(PointType.EXPIRE)
                        .amount(expireAmount)
                        .refId("BATCH_" + LocalDate.now()) // 참조 ID는 배치 실행일 등으로 기록
                        .build();

                // 상세 정보 추가
                history.addDetail(PointHistoryDetail.builder()
                        .pointItem(item)
                        .amount(expireAmount)
                        .build());

                histories.add(history);
            }

            // 2. 유저별 만료 금액 집계 (Wallet 차감을 위해 그룹핑)
            Map<Long, Long> userExpireMap = histories.stream()
                    .collect(Collectors.groupingBy(
                            PointHistory::getUserId,
                            Collectors.summingLong(PointHistory::getAmount)
                    ));

            // 3. UserPointWallet 잔액 차감 (Dirty Checking 활용)
            for (Map.Entry<Long, Long> entry : userExpireMap.entrySet()) {
                Long userId = entry.getKey();
                Long totalExpireAmount = entry.getValue();

                // Lock이 불필요하므로 findByUserId 사용
                userPointWalletRepository.findByUserId(userId)
                        .ifPresent(wallet -> wallet.use(totalExpireAmount));
            }

            // 4. 변경된 PointItem들을 명시적으로 저장 (Merge/Update)
            // ItemReader에서 가져온 엔티티의 상태가 변경되었으므로, 변경 내용 반영을 위해 saveAll(merge) 호출
            pointItemRepository.saveAll((List<PointItem>) items); // 캐스팅 필요

            // 5. 히스토리 일괄 저장
            pointHistoryRepository.saveAll(histories);
        };
    }
}