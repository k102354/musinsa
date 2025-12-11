package com.musinsa.payment.point.global.interceptor;

import com.musinsa.payment.point.global.annotation.AdminOnly;
import com.musinsa.payment.point.global.error.BusinessException;
import com.musinsa.payment.point.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    // application.yml에서 관리자 키를 가져옵니다.
    @Value("${musinsa.admin-key}")
    private String adminKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 요청을 처리하는 대상이 컨트롤러 메서드(HandlerMethod)인지 확인
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 2. @AdminOnly 어노테이션이 붙어있는지 확인
        AdminOnly adminOnly = handlerMethod.getMethodAnnotation(AdminOnly.class);
        if (adminOnly == null) {
            return true; // 어노테이션이 없으면 통과
        }

        // 3. 헤더 검증 (X-ADMIN-KEY)
        String requestKey = request.getHeader("X-ADMIN-KEY");
        if (requestKey == null || !requestKey.equals(adminKey)) {
            // 예외를 던지면 GlobalExceptionHandler가 받아서 처리함
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return true;
    }
}