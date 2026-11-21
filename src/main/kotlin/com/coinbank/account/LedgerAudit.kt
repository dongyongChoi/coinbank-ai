package com.coinbank.account

import com.coinbank.account.internal.LedgerEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 원장과 잔액 컬럼이 어긋난 곳을 찾는다. 테스트와 부하 테스트 뒤의 정합성 확인에 쓴다.
 * B안(ledger-only)에서는 잔액 컬럼을 쓰지 않으므로 mismatchedAccounts는 의미가 없다.
 */
@Service
class LedgerAudit(
    private val ledgerEntryRepository: LedgerEntryRepository,
) {

    @Transactional(readOnly = true)
    fun check() = LedgerAuditResult(
        mismatchedAccounts = ledgerEntryRepository.findAccountNumbersWhereBalanceDiffersFromLedger(),
        unbalancedTransactions = ledgerEntryRepository.findUnbalancedTransactionIds(),
    )
}

data class LedgerAuditResult(
    /** 잔액 컬럼이 원장 합산과 다른 계좌 번호. */
    val mismatchedAccounts: List<String>,
    /** 줄들의 합이 0이 아닌 거래 ID. */
    val unbalancedTransactions: List<String>,
) {
    val isConsistent: Boolean
        get() = mismatchedAccounts.isEmpty() && unbalancedTransactions.isEmpty()
}
