package com.coinbank.transfer.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** 송금 기록. 한 번 쓰면 고치지 않으므로 모든 필드가 val이다. */
@Entity
@Table(name = "transfer")
class Transfer(
    @Column(nullable = false, updatable = false, length = 12)
    val fromAccountNumber: String,

    @Column(nullable = false, updatable = false, length = 12)
    val toAccountNumber: String,

    @Column(nullable = false, updatable = false)
    val amount: Long,

    /**
     * 클라이언트가 요청마다 붙이는 고유한 값. 유니크 제약이 걸려 있어서
     * 같은 키의 송금은 DB에 한 줄만 들어갈 수 있다.
     */
    @Column(nullable = false, updatable = false, unique = true, length = 64)
    val idempotencyKey: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 이 송금이 원장에 남긴 줄들을 묶는 거래 ID. */
    @Column(nullable = false, updatable = false, length = 36)
    val transactionId: String = UUID.randomUUID().toString()

    @Column(nullable = false, updatable = false)
    // MySQL DATETIME(6)는 마이크로초까지만 저장한다. 리눅스 JVM의 Instant.now()는 나노초까지 있어서,
    // 자르지 않으면 저장 직후 응답과 DB에서 다시 읽은 값이 달라진다.
    val createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

    init {
        require(fromAccountNumber != toAccountNumber) { "같은 계좌로는 송금할 수 없습니다." }
        require(amount > 0) { "금액은 0보다 커야 합니다." }
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 64) {
            "Idempotency-Key는 1자 이상 64자 이하여야 합니다."
        }
    }
}
