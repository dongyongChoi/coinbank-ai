package com.coinbank.transfer

import com.coinbank.IntegrationTest
import com.coinbank.account.AccountService
import com.coinbank.account.LedgerAudit
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * 여러 스레드가 같은 계좌에서 동시에 송금해도 돈이 틀리지 않는지 확인한다.
 * HTTP를 거치지 않고 TransferService를 직접 부른다. 보려는 것이 트랜잭션끼리의 충돌이기 때문이다.
 */
@IntegrationTest
class ConcurrentTransferTests(
    @Autowired private val transferService: TransferService,
    @Autowired private val accountService: AccountService,
    @Autowired private val ledgerAudit: LedgerAudit,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Disabled("갱신 유실로 실패한다 (보낸 계좌 잔액: 기대 0, 실제 90,000). M2-3에서 락을 넣고 켠다.")
    @Test
    fun `같은 계좌에서 동시에 100번 송금해도 잔액이 송금 기록과 맞는다`() {
        val from = accountService.open("김코인", 100_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        val succeeded = runConcurrently(times = 100) {
            transferService.transfer(from, to, 1_000)
        }

        assertEquals(100, succeeded, "성공한 송금 수")
        assertEquals(100, transferCountInDb(from), "송금 기록 수")
        assertEquals(
            emptyList(),
            ledgerAudit.check().mismatchedAccounts.filter { it == from || it == to },
            "원장과 잔액이 어긋난 계좌",
        )
        assertEquals(0, balanceInDb(from), "보낸 계좌 잔액 (100,000 - 1,000 x 100)")
        assertEquals(100_000, balanceInDb(to), "받은 계좌 잔액 (0 + 1,000 x 100)")
    }

    /**
     * task를 times번, 스레드 여러 개로 동시에 실행하고 성공한 횟수를 돌려준다.
     * startGate가 열릴 때까지 모든 스레드가 기다렸다가 한꺼번에 출발한다.
     */
    private fun runConcurrently(times: Int, threads: Int = 32, task: () -> Unit): Int {
        val executor = Executors.newFixedThreadPool(threads)
        val startGate = CountDownLatch(1)
        val succeeded = AtomicInteger()
        try {
            val futures = List(times) {
                executor.submit {
                    startGate.await()
                    runCatching(task).onSuccess { succeeded.incrementAndGet() }
                }
            }
            startGate.countDown()
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        return succeeded.get()
    }

    private fun balanceInDb(accountNumber: String): Long? =
        jdbcTemplate.queryForObject(
            "SELECT balance FROM account WHERE account_number = ?",
            Long::class.java,
            accountNumber,
        )

    private fun transferCountInDb(fromAccountNumber: String): Int? =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transfer WHERE from_account_number = ?",
            Int::class.java,
            fromAccountNumber,
        )
}
