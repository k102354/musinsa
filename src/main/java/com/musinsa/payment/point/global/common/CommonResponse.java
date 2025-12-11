package com.musinsa.payment.point.global.common;

/**
 * API 공통 응답 포맷
 * @param result 성공 여부 (true/false)
 * @param message 응답 메시지 (성공 시 "OK", 실패 시 에러 메시지)
 * @param code 응답 코드 (성공 시 "200", 실패 시 커스텀 에러 코드)
 * @param data 실제 데이터 (실패 시 null)
 */
public record CommonResponse<T>(
        boolean result,
        String message,
        String code,
        T data
) {
    // 성공 시 응답 생성 (데이터 있음)
    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(true, "요청이 성공적으로 처리되었습니다.", "200", data);
    }

    // 성공 시 응답 생성 (데이터 없음)
    public static CommonResponse<Void> success() {
        return new CommonResponse<>(true, "요청이 성공적으로 처리되었습니다.", "200", null);
    }

    // 실패 시 응답 생성
    public static CommonResponse<Void> fail(String code, String message) {
        return new CommonResponse<>(false, message, code, null);
    }
}