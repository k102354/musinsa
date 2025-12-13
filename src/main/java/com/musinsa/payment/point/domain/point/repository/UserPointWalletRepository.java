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
     * - 적립/사용 이외의 단순 조회(마이페이지 등)에서 사용
     */
    Optional<UserPointWallet> findByUserId(Long userId);

    /**
     * [핵심] 포인트 변동 시 사용 (비관적 락 적용)
     * - SELECT ... FOR UPDATE 쿼리가 발생하여, 트랜잭션이 끝날 때까지 다른 요청이 대기합니다.
     * - 동시성 이슈(갱신 분실 등)를 원천 차단합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM UserPointWallet w WHERE w.userId = :userId")
    Optional<UserPointWallet> findByUserIdForUpdate(@Param("userId") Long userId);

    Optional<UserPointWallet> readByUserId(@Param("userId") Long userId);
}