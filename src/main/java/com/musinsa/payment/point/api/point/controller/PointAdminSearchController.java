package com.musinsa.payment.point.api.point.controller;

import com.musinsa.payment.point.api.point.dto.PointBalanceResponse;
import com.musinsa.payment.point.api.point.dto.PointHistoryResponse;
import com.musinsa.payment.point.api.point.dto.PointStatisticsResponse;
import com.musinsa.payment.point.application.point.service.PointAdminSearchService;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.global.common.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 포인트 조회 Controller (관리자 전용)
 * - URL Prefix: /api/v1/points/admin
 * - 권한: 관리자 권한을 가진 사용자만 접근 가능해야 함 (인터셉터/필터에서 권한 검증 필요)
 */
@RestController
@RequestMapping("/api/v1/points/admin")
@RequiredArgsConstructor
public class PointAdminSearchController {

    private final PointAdminSearchService pointAdminSearchService;

    /**
     * [관리자] 포인트 이력 통합 조회
     * - Method: GET /api/v1/points/admin/search
     * - 규칙: 조회 기간(startDate, endDate)은 필수 파라미터.
     * - 특징: userId가 선택적이어서 전체 사용자 이력 조회가 가능함.
     */
    @GetMapping("/search")
    public ResponseEntity<CommonResponse<Page<PointHistoryResponse>>> searchHistories(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate, // 필수: 시작일
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,   // 필수: 종료일
            @RequestParam(required = false) Long userId,     // [선택] 특정 사용자 ID 필터링
            @RequestParam(required = false) String refId,    // [선택] 거래번호 필터링
            @RequestParam(required = false) PointType type,  // [선택] 거래상태 필터링
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable // 페이징/정렬
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                pointAdminSearchService.getHistories(startDate, endDate, userId, refId, type, pageable)
        ));
    }

    /**
     * [관리자] 시스템 전체 잔여 포인트 조회
     * - Method: GET /api/v1/points/admin/remain/total
     * - 현재 사용가능한 잔액 총액 조회
     */
    @GetMapping("/remain/total")
    public ResponseEntity<CommonResponse<Long>> getTotalRemain() {
        return ResponseEntity.ok(CommonResponse.success(
                pointAdminSearchService.getTotalRemain()
        ));
    }

    /**
     * [관리자] 기간별 통계 (적립/사용 합계)
     * - Method: GET /api/v1/points/admin/statistics
     * - 월별/일별 포인트 발행 및 회수 현황 지표 산출.
     */
    @GetMapping("/statistics")
    public ResponseEntity<CommonResponse<List<PointStatisticsResponse>>> getStatistics(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                pointAdminSearchService.getStatistics(startDate, endDate)
        ));
    }

    /**
     * [관리자/CS] 특정 유저 잔액 조회
     * - Method: GET /api/v1/points/admin/users/{userId}/balance
     * - 고객 문의 시 해당 유저의 현재 잔액을 확인.
     */
    @GetMapping("/users/{userId}/balance")
    public ResponseEntity<CommonResponse<PointBalanceResponse>> getUserBalance(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                pointAdminSearchService.getUserBalance(userId)
        ));
    }
}