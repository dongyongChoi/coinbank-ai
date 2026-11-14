package com.coinbank.transfer.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

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
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    init {
        require(fromAccountNumber != toAccountNumber) { "같은 계좌로는 송금할 수 없습니다." }
        require(amount > 0) { "금액은 0보다 커야 합니다." }
    }
}
