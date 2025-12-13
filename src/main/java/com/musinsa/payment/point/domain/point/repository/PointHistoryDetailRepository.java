package com.musinsa.payment.point.domain.point.repository;

import com.musinsa.payment.point.domain.point.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryDetailRepository extends JpaRepository<PointHistory, Long> {
}