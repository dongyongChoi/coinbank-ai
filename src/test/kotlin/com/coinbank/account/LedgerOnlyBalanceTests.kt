package com.coinbank.account

import com.coinbank.IntegrationTest
import com.coinbank.transfer.TransferService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/** B안(원장 합산만). 잔액 컬럼 없이도 동시 송금에서 돈이 틀리지 않는지 확인한다. */
@IntegrationTest
@TestPropertySource(properties = ["coinbank.balance-model=ledger-only"])
class LedgerOnlyBalanceTests(
    @Autowired private val transferService: TransferService,
    @Autowired private val accountService: AccountService,
    @Autowired private val ledgerAudit: LedgerAudit,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `잔액은 원장 합산이고 동시에 100번 송금해도 맞는다`() {
        val from = accountService.open("김코인", 100_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        val failures = runConcurrently(times = 100) { transferService.transfer(from, to, 1_000, newKey()) }

        assertEquals(emptyList(), failures, "실패한 송금")
        assertEquals(0, accountService.find(from)?.balance, "보낸 계좌 잔액 (원장 합산)")
        assertEquals(100_000, accountService.find(to)?.balance, "받은 계좌 잔액 (원장 합산)")
        assertEquals(100_000, columnBalanceOf(from), "잔액 컬럼은 개설 때 값 그대로다")
        assertEquals(emptyList(), ledgerAudit.check().unbalancedTransactions, "합이 0이 아닌 거래")
    }

    @Test
    fun `잔액보다 많이 동시에 보내도 잔액이 음수가 되지 않는다`() {
        val from = accountService.open("김코인", 10_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        // 1,000원씩 20번: 10번만 성공해야 한다.
        val failures = runConcurrently(times = 20) { transferService.transfer(from, to, 1_000, newKey()) }

        assertEquals(List(10) { "InsufficientBalanceException" }, failures, "실패한 송금")
        assertEquals(0, accountService.find(from)?.balance, "보낸 계좌 잔액")
        assertEquals(10_000, accountService.find(to)?.balance, "받은 계좌 잔액")
    }

    private fun newKey(): String = UUID.randomUUID().toString()

    private fun columnBalanceOf(accountNumber: String): Long? =
        jdbcTemplate.queryForObject(
            "SELECT balance FROM account WHERE account_number = ?",
            Long::class.java,
            accountNumber,
        )

    /** task를 times번 동시에 실행하고 실패한 작업의 예외 이름을 돌려준다. */
    private fun runConcurrently(times: Int, threads: Int = 32, task: () -> Unit): List<String> {
        val executor = Executors.newFixedThreadPool(threads)
        val startGate = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<String>()
        try {
            val futures = List(times) {
                executor.submit {
                    startGate.await()
                    runCatching(task).onFailure { failures += it.javaClass.simpleName }
                }
            }
            startGate.countDown()
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        return failures.toList()
    }
}
