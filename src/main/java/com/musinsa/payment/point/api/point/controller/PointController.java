package com.musinsa.payment.point.api.point.controller;

import com.musinsa.payment.point.api.point.dto.PointCancelEarnRequest;
import com.musinsa.payment.point.api.point.dto.PointCancelUseRequest;
import com.musinsa.payment.point.api.point.dto.PointEarnRequest;
import com.musinsa.payment.point.api.point.dto.PointUseRequest;
import com.musinsa.payment.point.application.point.service.PointService; // Facade 대신 Service import
import com.musinsa.payment.point.global.common.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 Command Controller (적립, 사용, 취소 등 쓰기 작업)
 * - URL Prefix: /api/v1/points
 * - 보안: 모든 API는 내부 시스템(주문/이벤트) 또는 인증된 API Gateway를 통해 호출되어야 함.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    /**
     * 포인트 적립 API (EARN)
     * - Method: POST /api/v1/points/earn
     * - 역할: 주문 완료, 이벤트 참여 등 적립 이벤트 발생 시 호출됨.
     * - 특징: 요청 DTO(@Valid)를 통해 userId, amount, isManual 유효성 검사 수행.
     */
    @PostMapping("/earn")
    public ResponseEntity<CommonResponse<Void>> earn(@RequestBody @Valid PointEarnRequest request) {
        log.info("PointController.earn request : {}", request);
        pointService.earn(request.userId(),request.amount(), request.isManual());
        return ResponseEntity.ok(CommonResponse.success());
    }

    /**
     * 적립 취소 API (EARN_CANCEL)
     * - Method: POST /api/v1/points/earn/cancel
     * - 역할: 지급된 특정 PointItem(원장) 전체를 취소(회수) 처리함.
     * - 제약: 포인트가 "전액 미사용 상태"일 때만 취소 가능 (Service에서 검증).
     */
    @PostMapping("/earn/cancel")
    public ResponseEntity<CommonResponse<Void>> cancelEarn(@RequestBody @Valid PointCancelEarnRequest request) {
        log.info("PointController.cancelEarn request : {}", request);
        pointService.cancelEarn(request.userId(), request.pointItemId(), request.isManual());
        return ResponseEntity.ok(CommonResponse.success());
    }

    /**
     * 포인트 사용 API (USE)
     * - Method: POST /api/v1/points/use
     * - 역할: 주문 시스템 등에서 포인트 사용 요청 시 호출됨.
     * - 특징: orderId를 통해 "멱등성 검사"를 수행하여 중복 사용을 방지함.
     */
    @PostMapping("/use")
    public ResponseEntity<CommonResponse<Void>> use(@RequestBody @Valid PointUseRequest request) {
        log.info("PointController.use request : {}", request);
        pointService.use(request.userId(), request.amount(), request.orderId());
        return ResponseEntity.ok(CommonResponse.success());
    }

    /**
     * 포인트 사용 취소 API (USE_CANCEL / RESTORE)
     * - Method: POST /api/v1/points/use/cancel
     * - 역할: 주문 취소/환불 발생 시 호출되어, 사용된 포인트를 복구함.
     * - 로직 특징: 만료 여부에 따라 "유효분은 취소(USE_CANCEL), 만료분은 신규 재적립(RESTORE)"으로 분기 처리함.
     */
    @PostMapping("/use/cancel")
    public ResponseEntity<CommonResponse<Void>> cancelUse(@RequestBody @Valid PointCancelUseRequest request) {
        log.info("PointController.cancelUse request : {}", request);
        pointService.cancelUse(request.userId(), request.orderId(), request.cancelAmount());
        return ResponseEntity.ok(CommonResponse.success());
    }
}