package com.musinsa.payment.point.global.util;

import io.hypersistence.tsid.TSID;

/**
 * TSID (Time-Sorted Unique Identifier) 생성 유틸리티
 *
 * <p>
 * <b>왜 TSID인가?</b>
 * 1. <b>Long 타입 (64bit):</b> UUID(128bit)보다 저장 공간을 절반만 차지하며, DB 인덱싱 효율이 좋다.
 * 2. <b>가독성:</b> "550e8400..." 같은 문자열 대신 "492810..." 같은 숫자로 보여 디버깅이 편하다.
 * 3. <b>시간순 정렬 (Time-Sorted):</b> 생성된 ID가 시간 순서대로 커짐을 보장한다.
 * - DB INSERT 시 인덱스 조각화(Fragmentation)가 발생하지 않아 성능이 뛰어나다.
 * - 별도의 `createdAt` 정렬 없이 ID만으로 최신순 정렬이 가능하다.
 * </p>
 */
public class TsidUtil {

    /**
     * 시간 순서대로 정렬되는 유니크한 Long ID를 반환한다.
     * 예: 4928194820194821
     */
    public static Long nextId() {
        return TSID.fast().toLong();
    }
}