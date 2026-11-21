package com.coinbank.account.internal

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository

/**
 * JpaRepository가 아니라 Repository를 상속해서 필요한 메서드만 연다.
 * 원장은 고치거나 지우지 않으므로 delete 계열 메서드를 아예 만들지 않는다.
 */
interface LedgerEntryRepository : Repository<LedgerEntry, Long> {

    fun save(entry: LedgerEntry): LedgerEntry

    /** 한 계좌의 원장 합산. B안(원장 합산만)에서는 이 값이 곧 잔액이다. */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.accountNumber = :accountNumber")
    fun sumAmountByAccountNumber(accountNumber: String): Long

    /** 잔액 컬럼이 그 계좌의 원장 합산과 다른 계좌 번호. */
    @Query(
        """
        select a.accountNumber from Account a
        where a.balance <> (
            select coalesce(sum(e.amount), 0) from LedgerEntry e
            where e.accountNumber = a.accountNumber
        )
        """,
    )
    fun findAccountNumbersWhereBalanceDiffersFromLedger(): List<String>

    /** 줄들의 합이 0이 아닌 거래. 복식 부기가 깨진 곳이다. */
    @Query("select e.transactionId from LedgerEntry e group by e.transactionId having sum(e.amount) <> 0")
    fun findUnbalancedTransactionIds(): List<String>
}
