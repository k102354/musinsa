package com.musinsa.payment.point.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointJobScheduler {

    private final JobLauncher jobLauncher;
    private final Job pointExpireJob;

    // 매일 자정 (00:00:00) 실행
    @Scheduled(cron = "0 0 0 * * *")
    public void runExpireJob() {
        try {
            log.info(">>> 포인트 만료 배치 시작");

            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("targetDate", LocalDate.now().toString())
                    .addLong("time", System.currentTimeMillis()) // 중복 실행 방지용 파라미터
                    .toJobParameters();

            jobLauncher.run(pointExpireJob, jobParameters);

            log.info(">>> 포인트 만료 배치 종료");
        } catch (Exception e) {
            log.error(">>> 포인트 만료 배치 실패", e);
        }
    }
}