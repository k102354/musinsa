package com.musinsa.payment.point.domain.repository;

import com.musinsa.payment.point.domain.entity.PointPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointPolicyRepository extends JpaRepository<PointPolicy, Long> {

    /**
     * 가장 최근에 등록된 정책 1건을 조회한다.
     * ID를 기준으로 내림차순 정렬하여 가장 위의 1개를 가져온다.
     * * 실행되는 쿼리: SELECT * FROM point_policy ORDER BY id DESC LIMIT 1
     */
    Optional<PointPolicy> findTopByOrderByIdDesc();
}