package com.musinsa.payment.point.domain.point.enums;

public enum PointType {
    EARN,           // 적립
    EARN_CANCEL,
    USE,            // 사용 (차감)
    USE_CANCEL,         // 사용 취소 (주문 취소로 인한 복구)
    EXPIRE,         // 만료
    RESTORE,        // 만료 재적립 (보상)
    ADMIN_REVOKE,    // 관리자 회수
    ADMIN_GRANT
}