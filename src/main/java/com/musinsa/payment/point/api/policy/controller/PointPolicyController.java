package com.musinsa.payment.point.api.policy.controller;

import com.musinsa.payment.point.api.policy.dto.PointPolicyUpdateRequest;
import com.musinsa.payment.point.application.policy.service.PointPolicyService;
import com.musinsa.payment.point.global.annotation.AdminOnly;
import com.musinsa.payment.point.global.common.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 포인트 정책 관리 컨트롤러 (관리자 전용)
 * - URL Prefix: /api/v1/admin/points/policies
 * - 역할: 포인트 적립 한도, 보유 한도, 만료일자 등 핵심 비즈니스 정책을 동적으로 변경함.
 */
@RestController
@RequestMapping("/api/v1/admin/points/policies")
@RequiredArgsConstructor
public class PointPolicyController {

    private final PointPolicyService pointPolicyService;

    /**
     * 포인트 정책 변경 (Admin - PUT)
     * - Method: PUT /api/v1/admin/points/policies
     * - 보안: @AdminOnly 어노테이션으로 관리자 인증/권한 검증을 강제함.
     * - 특징: 변경 요청 시 DB에 정책 이력을 저장하고, PointPolicyManager를 통해 **실행 중인 서비스의 캐시를 즉시 갱신**하여 무중단 반영을 실현함.
     *
     * @param request 변경할 정책 값 (부분 변경 가능, @Valid 및 isAtLeastOneFieldPresent() 검증)
     * @return 성공 응답 (CommonResponse)
     */
    @AdminOnly // AdminAuthorizationInterceptor 헤더의 X-ADMIN-KEY 체크 하도록 설정하는 어노테이션
    @PutMapping
    public ResponseEntity<CommonResponse<Void>> updatePolicy(@RequestBody @Valid PointPolicyUpdateRequest request) {
        pointPolicyService.updatePolicy(request);
        return ResponseEntity.ok(CommonResponse.success());
    }
}