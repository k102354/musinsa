package com.musinsa.payment.point.api.point.controller;

import com.musinsa.payment.point.api.point.dto.PointBalanceResponse;
import com.musinsa.payment.point.api.point.dto.PointExpiringResponse;
import com.musinsa.payment.point.api.point.dto.PointHistoryResponse;
import com.musinsa.payment.point.application.point.service.PointSearchService;
import com.musinsa.payment.point.domain.point.enums.PointType;
import com.musinsa.payment.point.global.common.CommonResponse; // 공통 응답 패키지 경로 가정
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
 * 포인트 조회 Controller (사용자 전용)
 * - URL Prefix: /api/v1/points
 * - 보안: X-User-Id 헤더를 통해 인증된 사용자 식별자를 수신함.
 */
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointSearchController {

    private final PointSearchService pointSearchService;

    /**
     * [사용자] 내 포인트 이력 조회
     * - Method: GET /api/v1/points/search
     * - 제약: 조회 기간 필수, 최대 3개월 제한 규칙 적용 (Service에서 검증)
     * @param userId 인증 헤더에서 추출된 사용자 ID (필수)
     */
    @GetMapping("/search")
    public ResponseEntity<CommonResponse<Page<PointHistoryResponse>>> searchMyHistories(
            @RequestHeader("X-User-Id") Long userId, // 실제 서비스시에서는 인증 인터셉터가 토큰 복호화 후 Attribute에 넣거나, 암호화된 ID를 받음.
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate, // 필수: 시작일
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,   // 필수: 종료일
            @RequestParam(required = false) String refId,    // [선택] 거래번호 필터링
            @RequestParam(required = false) PointType type,  // [선택] 거래상태 필터링
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable // 페이징/정렬
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                pointSearchService.getMyHistories(userId, startDate, endDate, refId, type, pageable)
        ));
    }

    /**
     * [사용자] 잔액 조회
     * - Method: GET /api/v1/points/balance
     * - 성능: UserPointWallet 테이블에서 잔액을 빠르게 조회.
     */
    @GetMapping("/balance")
    public ResponseEntity<CommonResponse<PointBalanceResponse>> getMyBalance(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                pointSearchService.getMyBalance(userId)
        ));
    }

    /**
     * [사용자] 30일 내 소멸 예정 포인트
     * - Method: GET /api/v1/points/expiring
     * - 만료 임박 알림 제공.
     */
    @GetMapping("/expiring")
    public ResponseEntity<CommonResponse<List<PointExpiringResponse>>> getExpiringPointsList(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                pointSearchService.getListExpiringPointItemsIn30Days(userId)
        ));
    }

}