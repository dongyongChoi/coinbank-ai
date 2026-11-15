package com.coinbank.account.internal

import com.coinbank.account.InsufficientBalanceException
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

    /** 원 단위 잔액. 빠른 조회용이고, 근거는 원장(LedgerEntry)에 있다. 둘은 항상 같아야 한다. */
    @Column(nullable = false)
    var balance: Long = initialBalance
        protected set

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    init {
        require(ownerName.isNotBlank()) { "예금주 이름은 비어 있을 수 없습니다." }
        require(initialBalance >= 0) { "초기 잔액은 0 이상이어야 합니다." }
    }

    fun withdraw(amount: Long) {
        require(amount > 0) { "금액은 0보다 커야 합니다." }
        if (balance < amount) throw InsufficientBalanceException(accountNumber, balance, amount)
        balance -= amount
    }

    fun deposit(amount: Long) {
        require(amount > 0) { "금액은 0보다 커야 합니다." }
        balance += amount
    }
}
