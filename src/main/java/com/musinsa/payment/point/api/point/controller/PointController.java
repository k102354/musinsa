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

@Slf4j
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    // [변경] Facade 제거 -> Service 직접 주입
    private final PointService pointService;

    /**
     * 포인트 적립 API
     */
    @PostMapping("/earn")
    public ResponseEntity<CommonResponse<Void>> earn(@RequestBody @Valid PointEarnRequest request) {
        log.info("PointController.earn request : {}", request);
        pointService.earn(request.userId(),request.amount(), request.isManual());
        return ResponseEntity.ok(CommonResponse.success());
    }

    /**
     * 적립 취소 API
     * (전액 미사용 상태일 때만 가능)
     */
    @PostMapping("/earn/cancel")
    public ResponseEntity<CommonResponse<Void>> cancelEarn(@RequestBody @Valid PointCancelEarnRequest request) {
        log.info("PointController.cancelEarn request : {}", request);
        pointService.cancelEarn(request.userId(), request.pointItemId(), request.isManual());
        return ResponseEntity.ok(CommonResponse.success());
    }

    /**
     * 포인트 사용 API
     * (주문 발생 시 호출됨)
     */
    @PostMapping("/use")
    public ResponseEntity<CommonResponse<Void>> use(@RequestBody @Valid PointUseRequest request) {
        log.info("PointController.use request : {}", request);
        pointService.use(request.userId(), request.amount(), request.orderId());
        return ResponseEntity.ok(CommonResponse.success());
    }

    /**
     * 포인트 사용 취소 API
     * (주문 취소/환불 시 호출됨)
     */
    @PostMapping("/use/cancel")
    public ResponseEntity<CommonResponse<Void>> cancelUse(@RequestBody @Valid PointCancelUseRequest request) {
        log.info("PointController.cancelUse request : {}", request);
        pointService.cancelUse(request.userId(), request.orderId(), request.cancelAmount());
        return ResponseEntity.ok(CommonResponse.success());
    }
}