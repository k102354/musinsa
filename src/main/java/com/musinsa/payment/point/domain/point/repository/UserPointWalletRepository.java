package com.musinsa.payment.point.domain.point.repository;

import com.musinsa.payment.point.domain.point.entity.UserPointWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserPointWalletRepository extends JpaRepository<UserPointWallet, Long> {

    /**
     * 단순 지갑 조회
     * - 적립/사용 트랜잭션 외부에서 사용 (예: 마이페이지 잔액 표시)
     * - Lock이 걸리지 않아 조회 성능이 빠름.
     */
    Optional<UserPointWallet> findByUserId(Long userId);

    /**
     * 포인트 변동 시 사용 (비관적 락 적용)
     * - SELECT ... FOR UPDATE 쿼리가 발생함.
     * - 목적: UserPointWallet의 잔액(balance) 업데이트 시 동시성 이슈(갱신 분실)를 원천 차단
     * - Lock: 트랜잭션이 종료될 때까지 해당 row에 대한 쓰기 접근을 막아 데이터 정합성을 보장함.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM UserPointWallet w WHERE w.userId = :userId")
    Optional<UserPointWallet> findByUserIdForUpdate(@Param("userId") Long userId);

    /** * 테스트용 일반 조회
     * - 비즈니스 로직(Lock 버전)과의 혼동을 막기 위해 read 접두사 사용
     * */
    Optional<UserPointWallet> readByUserId(@Param("userId") Long userId);
}