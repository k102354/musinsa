package com.musinsa.payment.point.domain.policy.repository;

import com.musinsa.payment.point.domain.policy.entity.PointPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointPolicyRepository extends JpaRepository<PointPolicy, Long> {

    /**
     * 가장 최근에 등록된 정책 1건을 조회한다.
     * - 현재 서비스에 적용해야 할 활성(Active) 정책을 가져옴.
     * - 전략: ID(자동 증가 값)를 기준으로 내림차순 정렬하여 가장 최근의 1개만 조회 (findTopByOrderByIdDesc)
     * - 쿼리: SELECT * FROM point_policy ORDER BY id DESC LIMIT 1 (효율적인 단일 로우 조회)
     */
    Optional<PointPolicy> findTopByOrderByIdDesc();
}