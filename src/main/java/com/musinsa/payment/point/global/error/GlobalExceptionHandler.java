package com.musinsa.payment.point.global.error;

import com.musinsa.payment.point.global.common.CommonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. 비즈니스 로직 에러 (BusinessException) 처리
     * 개발자가 의도적으로 발생시킨 예외
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("Business Exception: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(CommonResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * 2. @Valid 유효성 검사 실패 (MethodArgumentNotValidException) 처리
     * DTO의 @NotNull, @Min 등 검증 실패 시 발생
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<CommonResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("Validation Failed: {}", e.getMessage());
        BindingResult bindingResult = e.getBindingResult();

        // 첫 번째 에러 메시지만 가져옵니다.
        String firstErrorMessage = bindingResult.getFieldError() != null
                ? bindingResult.getFieldError().getDefaultMessage()
                : "잘못된 입력값입니다.";

        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(CommonResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getCode(), firstErrorMessage));
    }

    /**
     * 3. IllegalArgumentException 처리
     * 엔티티 내부 로직(validate) 등에서 던진 예외를 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<CommonResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal Argument: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(CommonResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getCode(), e.getMessage()));
    }

    /**
     * 4. 나머지 모든 서버 에러 (Exception) 처리
     * 예상치 못한 에러가 사용자에게 그대로 노출되지 않도록 방어
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<CommonResponse<Void>> handleException(Exception e) {
        log.error("Internal Server Error", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(CommonResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}