package com.coinbank.account.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "account")
class Account(
    @Column(nullable = false, unique = true, length = 12)
    val accountNumber: String,

    @Column(nullable = false, length = 50)
    val ownerName: String,

    initialBalance: Long = 0,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 원 단위 잔액. M2에서 원장 합산으로 바꾼다. */
    @Column(nullable = false)
    var balance: Long = initialBalance
        protected set

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    init {
        require(ownerName.isNotBlank()) { "예금주 이름은 비어 있을 수 없습니다." }
        require(initialBalance >= 0) { "초기 잔액은 0 이상이어야 합니다." }
    }
}
