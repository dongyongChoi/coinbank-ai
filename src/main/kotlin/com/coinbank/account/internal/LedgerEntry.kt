package com.coinbank.account.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * 원장 한 줄. 한 계좌의 돈이 얼마나 늘거나 줄었는지 적는다.
 * 복식 부기라서 거래 하나(transactionId)에 속한 줄들의 amount를 모두 더하면 0이다.
 * 한 번 쓰면 고치지 않으므로 모든 필드가 val이다.
 */
@Entity
@Table(
    name = "ledger_entry",
    indexes = [
        Index(name = "idx_ledger_entry_account", columnList = "account_number"),
        Index(name = "idx_ledger_entry_transaction", columnList = "transaction_id"),
    ],
)
class LedgerEntry(
    @Column(nullable = false, updatable = false, length = 36)
    val transactionId: String,

    @Column(nullable = false, updatable = false, length = 12)
    val accountNumber: String,

    /** 원 단위. 입금은 양수, 출금은 음수. */
    @Column(nullable = false, updatable = false)
    val amount: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    init {
        require(amount != 0L) { "원장 금액은 0일 수 없습니다." }
    }

    companion object {
        /** 은행 바깥을 뜻하는 가상의 계좌. 계좌 개설 때 들어온 돈의 상대편으로 쓴다. */
        const val EXTERNAL = "EXTERNAL"
    }
}
