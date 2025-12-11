package com.musinsa.payment.point.global.config;

import com.musinsa.payment.point.global.interceptor.AdminAuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthorizationInterceptor adminAuthorizationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthorizationInterceptor)
                .addPathPatterns("/api/**"); // 모든 API 경로에 대해 검사 (어노테이션이 있는 경우만 작동)
    }
}