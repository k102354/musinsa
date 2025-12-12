package com.musinsa.payment.point.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 Entity의 상위 클래스가 되어 생성일/수정일을 자동으로 관리한다.
 */
@Getter // Lombok: 자식 클래스에서 createdAt, updatedAt에 접근할 수 있도록 Getter 생성
@MappedSuperclass // JPA: 이 클래스는 테이블로 생성되지 않고, 상속받는 자식 Entity(UserPoint 등)에게 컬럼 정보만 제공합니다.
@EntityListeners(AuditingEntityListener.class) // JPA: 엔티티의 영속성 및 수정 이벤트를 감지하는 리스너를 등록합니다. (Spring Data JPA 제공)
public abstract class BaseTimeEntity {

    @CreatedDate // Spring Data JPA: 엔티티가 생성되어 저장될 때(persist) 시간이 자동 저장됩니다.
    @Column(updatable = false) // JPA: 생성일은 최초 저장 후 절대 변경되면 안 되므로, Update 쿼리에서 제외합니다.
    private LocalDateTime createdAt;

    @LastModifiedDate // Spring Data JPA: 조회한 엔티티의 값을 변경할 때(dirty checking) 시간이 자동 업데이트됩니다.
    private LocalDateTime updatedAt;
}