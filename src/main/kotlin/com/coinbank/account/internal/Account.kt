package com.coinbank.account.internal

import com.coinbank.account.InsufficientBalanceException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.temporal.ChronoUnit

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

    /**
     * 낙관 락용 버전. 행을 고칠 때마다 1씩 오른다. 저장할 때 "내가 읽은 version 그대로인가"를
     * UPDATE의 WHERE 조건으로 확인하고, 그사이 누가 먼저 고쳤으면 저장이 실패한다.
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    @Column(nullable = false, updatable = false)
    // MySQL DATETIME(6)는 마이크로초까지만 저장한다. 리눅스 JVM의 Instant.now()는 나노초까지 있어서,
    // 자르지 않으면 저장 직후 응답과 DB에서 다시 읽은 값이 달라진다.
    val createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

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
