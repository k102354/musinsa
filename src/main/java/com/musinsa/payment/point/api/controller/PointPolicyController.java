package com.musinsa.payment.point.api.controller;

import com.musinsa.payment.point.api.dto.PointPolicyUpdateRequest;
import com.musinsa.payment.point.application.service.PointPolicyService;
import com.musinsa.payment.point.global.annotation.AdminOnly;
import com.musinsa.payment.point.global.common.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 포인트 정책 관리 컨트롤러
 * <p>
 * 요구사항[적립] 1.1, 1.2, 1.5 (적립 한도, 보유 한도, 만료일자 제어)를 충족하기 위해
 * 외부(API) 요청으로 정책을 동적으로 변경할 수 있도록 제공한다.
 * </p>
 */
@RestController
@RequestMapping("/api/admin/points/policies")
@RequiredArgsConstructor
public class PointPolicyController {

    private final PointPolicyService pointPolicyService;

    /**
     * 포인트 정책 변경 (Admin)
     * <p>
     * 정책 변경 시 DB에 이력을 저장하고,
     * 실행 중인 어플리케이션의 인메모리 캐시(PointPolicyManager)를 즉시 갱신하여
     * 서버 재시작 없이 변경 사항을 무중단으로 반영한다.
     * </p>
     *
     * @param request 변경할 정책 값 (@Valid를 통해 1차 검증 수행)
     * @return 성공 여부 (CommonResponse)
     */
    @AdminOnly // ★ 이 어노테이션 하나로 보안 적용 완료
    @PutMapping
    public ResponseEntity<CommonResponse<Void>> updatePolicy(@RequestBody @Valid PointPolicyUpdateRequest request) {
        pointPolicyService.updatePolicy(request);
        // 성공 응답: result=true, code=200, data=null
        return ResponseEntity.ok(CommonResponse.success());
    }
}