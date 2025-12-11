package com.musinsa.payment.point.global.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 관리자용 API Key (Secret Key) 생성 유틸리티
 * <p>
 * java.security.SecureRandom을 사용하여 난수를 생성.
 * 이 클래스의 main 메서드를 실행하여 나온 값을 application.yml에 복사해서 사용.
 * </p>
 */
public class AdminKeyGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();

    /**
     * @param byteLength 생성할 키의 바이트 길이 (32바이트 = 256비트 권장)
     * @return Base64Url 인코딩된 안전한 문자열
     */
    public static String generateKey(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);
        // URL-Safe Base64로 인코딩 (특수문자 +, / 대신 -, _ 사용으로 헤더 전송 시 안전)
        return base64Encoder.withoutPadding().encodeToString(randomBytes);
    }

    public static void main(String[] args) {
        System.out.println("=== Musinsa Payment Admin Key Generator ===");

        // 1. 일반적인 32바이트 (256비트) 키 - 권장
        String key256 = generateKey(32);
        System.out.println("\n[Standard Key (256-bit)]");
        System.out.println(key256);

        // 2. 매우 강력한 64바이트 (512비트) 키
        String key512 = generateKey(64);
        System.out.println("\n[Strong Key (512-bit)]");
        System.out.println(key512);

        System.out.println("\n===========================================");
        System.out.println("※ 주의: 이 키를 application.yml에 복사하고, 절대 Git에 커밋하지 마세요.");
        System.out.println("  (환경 변수나 Jasypt 암호화를 사용하는 것을 권장합니다.)");
    }
}