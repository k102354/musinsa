package com.musinsa.payment.point.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "C003", "관리자 접근 권한이 없습니다."),

    // Point Domain
    POINT_NOT_FOUND(HttpStatus.BAD_REQUEST, "P001", "존재하지 않는 포인트 내역입니다."),
    BALANCE_INSUFFICIENT(HttpStatus.BAD_REQUEST, "P002", "잔액이 부족합니다."),
    MAX_POSSESSION_EXCEEDED(HttpStatus.BAD_REQUEST, "P003", "보유 한도를 초과했습니다."),
    INVALID_POINT_AMOUNT(HttpStatus.BAD_REQUEST, "P004", "유효하지 않은 포인트 금액입니다."),

    // Policy Domain
    POLICY_NOT_FOUND(HttpStatus.BAD_REQUEST, "P501", "운영 정책 데이터가 존재하지 않습니다."); // 500 에러

    private final HttpStatus status;
    private final String code;
    private final String message;
}