package com.musinsa.payment.point.global.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // 필요 시 메시지를 덮어쓰기 위한 생성자
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 잘못된 입력값 (C001) 예외 생성
     * 사용법: throw BusinessException.invalid("메시지");
     */
    public static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
    }

    /**
     * 데이터 없음 (P001) 예외 생성
     * 사용법: throw BusinessException.notFound("메시지");
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.POINT_NOT_FOUND, message);
    }

}