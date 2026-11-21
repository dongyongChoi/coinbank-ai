package com.coinbank.account

import com.coinbank.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@IntegrationTest
class LedgerTests(
    @Autowired private val accountService: AccountService,
    @Autowired private val ledgerAudit: LedgerAudit,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `계좌를 열면 초기 잔액이 원장에 남는다`() {
        val account = accountService.open("김코인", 5_000).accountNumber

        assertEquals(5_000, ledgerSumOf(account))
        assertConsistent(account)
    }

    @Test
    fun `돈을 옮기면 원장에 두 줄이 남고 거래의 합은 0이다`() {
        val from = accountService.open("김코인", 10_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber
        val transactionId = UUID.randomUUID().toString()

        accountService.moveMoney(from, to, 3_000, transactionId)

        assertEquals(listOf(-3_000L, 3_000L), amountsOf(transactionId))
        assertEquals(7_000, ledgerSumOf(from))
        assertEquals(3_000, ledgerSumOf(to))
        assertConsistent(from, to)
    }

    @Test
    fun `잔액이 부족해 실패하면 원장에 남지 않는다`() {
        val from = accountService.open("김코인", 1_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber
        val transactionId = UUID.randomUUID().toString()

        assertFailsWith<InsufficientBalanceException> {
            accountService.moveMoney(from, to, 1_001, transactionId)
        }

        assertEquals(emptyList(), amountsOf(transactionId))
        assertConsistent(from, to)
    }

    @Test
    fun `잔액 컬럼이 원장과 어긋나면 검사가 그 계좌를 찾아낸다`() {
        val account = accountService.open("김코인", 5_000).accountNumber

        // 원장을 거치지 않고 잔액만 바꾼다. 갱신 유실이 났을 때와 같은 상태다.
        jdbcTemplate.update("UPDATE account SET balance = balance + 1 WHERE account_number = ?", account)
        try {
            assertContains(ledgerAudit.check().mismatchedAccounts, account)
        } finally {
            // 다른 테스트와 DB를 같이 쓰므로 되돌려 놓는다.
            jdbcTemplate.update("UPDATE account SET balance = balance - 1 WHERE account_number = ?", account)
        }
    }

    private fun assertConsistent(vararg accountNumbers: String) {
        val result = ledgerAudit.check()
        accountNumbers.forEach { assertFalse(it in result.mismatchedAccounts, "원장과 잔액이 다른 계좌: $it") }
        assertEquals(emptyList(), result.unbalancedTransactions, "합이 0이 아닌 거래")
    }

    private fun ledgerSumOf(accountNumber: String): Long? =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM ledger_entry WHERE account_number = ?",
            Long::class.java,
            accountNumber,
        )

    private fun amountsOf(transactionId: String): List<Long> =
        jdbcTemplate.queryForList(
            "SELECT amount FROM ledger_entry WHERE transaction_id = ? ORDER BY amount",
            Long::class.java,
            transactionId,
        ).filterNotNull()
}
