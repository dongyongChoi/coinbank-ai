package com.coinbank.product.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 특판 적금 가입 기록. 한 번 쓰면 고치지 않는다.
 * 상품은 같은 모듈이지만 엔티티 연관(@ManyToOne) 대신 ID만 들고 있다. 가입 수를 셀 때 상품을 읽을 필요가 없다.
 */
@Entity
@Table(name = "subscription", indexes = [Index(columnList = "productId")])
class Subscription(
    @Column(nullable = false, updatable = false)
    val productId: Long,

    @Column(nullable = false, updatable = false, length = 12)
    val accountNumber: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    // MySQL DATETIME(6)는 마이크로초까지만 저장한다. 자르지 않으면 저장 직후 응답과 DB에서 다시 읽은 값이 달라진다.
    val createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
}
