package com.coinbank.transfer

import com.coinbank.IntegrationTest
import com.coinbank.RedisTestcontainersConfiguration
import com.coinbank.account.AccountService
import com.coinbank.account.LedgerAudit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals

/** 비관 락. application.yml의 기본값이라 따로 설정하지 않는다. */
class PessimisticLockTransferTests : ConcurrentTransferTests()

/** 낙관 락. 설정이 달라서 스프링 컨텍스트와 MySQL 컨테이너가 따로 뜬다. */
@TestPropertySource(properties = ["coinbank.lock-strategy=optimistic"])
class OptimisticLockTransferTests : ConcurrentTransferTests()

/** Redis 분산 락. Redis 컨테이너도 함께 띄운다. */
@TestPropertySource(properties = ["coinbank.lock-strategy=redis"])
@Import(RedisTestcontainersConfiguration::class)
class RedisLockTransferTests : ConcurrentTransferTests()

/**
 * 여러 스레드가 같은 계좌에서 동시에 송금해도 돈이 틀리지 않는지 확인한다.
 * HTTP를 거치지 않고 TransferService를 직접 부른다. 보려는 것이 트랜잭션끼리의 충돌이기 때문이다.
 * 같은 시나리오를 락 전략마다 돌리려고 abstract로 두고, 위의 하위 클래스가 전략을 고른다.
 */
@IntegrationTest
abstract class ConcurrentTransferTests {

    // 하위 클래스가 생성자 인자를 일일이 넘기지 않도록 필드로 주입받는다.
    @Autowired private lateinit var transferService: TransferService
    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var ledgerAudit: LedgerAudit
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `같은 계좌에서 동시에 100번 송금해도 잔액이 송금 기록과 맞는다`() {
        val from = accountService.open("김코인", 100_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        val failures = runConcurrently(times = 100) {
            transferService.transfer(from, to, 1_000, newKey())
        }

        assertEquals(emptyList(), failures, "실패한 송금")
        assertEquals(100, transferCountInDb(from), "송금 기록 수")
        assertEquals(emptyList(), mismatchedAmong(from, to), "원장과 잔액이 어긋난 계좌")
        assertEquals(0, balanceInDb(from), "보낸 계좌 잔액 (100,000 - 1,000 x 100)")
        assertEquals(100_000, balanceInDb(to), "받은 계좌 잔액 (0 + 1,000 x 100)")
    }

    @Test
    fun `서로 반대 방향으로 동시에 송금해도 데드락 없이 모두 성공한다`() {
        val a = accountService.open("김코인", 100_000).accountNumber
        val b = accountService.open("이뱅크", 100_000).accountNumber

        // 짝수 번째는 a -> b, 홀수 번째는 b -> a로 50건씩 보낸다.
        val failures = runConcurrently(times = 100) { i ->
            if (i % 2 == 0) transferService.transfer(a, b, 1_000, newKey()) else transferService.transfer(b, a, 1_000, newKey())
        }

        assertEquals(emptyList(), failures, "실패한 송금")
        assertEquals(emptyList(), mismatchedAmong(a, b), "원장과 잔액이 어긋난 계좌")
        assertEquals(100_000, balanceInDb(a), "a 잔액 (50건 보내고 50건 받음)")
        assertEquals(100_000, balanceInDb(b), "b 잔액 (50건 보내고 50건 받음)")
    }

    @Test
    fun `같은 Idempotency-Key로 동시에 20번 요청해도 송금은 한 번만 된다`() {
        val from = accountService.open("김코인", 100_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber
        val key = newKey()
        val transferIds = ConcurrentLinkedQueue<Long>()

        val failures = runConcurrently(times = 20) {
            transferIds += transferService.transfer(from, to, 1_000, key).transferId
        }

        assertEquals(emptyList(), failures, "실패한 요청")
        assertEquals(1, transferIds.distinct().size, "20번 모두 같은 송금 번호를 받는다")
        assertEquals(1, transferCountInDb(from), "송금 기록 수")
        assertEquals(emptyList(), mismatchedAmong(from, to), "원장과 잔액이 어긋난 계좌")
        assertEquals(99_000, balanceInDb(from), "보낸 계좌 잔액 (1,000원이 한 번만 빠짐)")
        assertEquals(1_000, balanceInDb(to), "받은 계좌 잔액")
    }

    private fun newKey(): String = UUID.randomUUID().toString()

    /**
     * task를 times번(0번째부터), 스레드 여러 개로 동시에 실행하고 실패한 작업의 예외 이름을 돌려준다.
     * startGate가 열릴 때까지 모든 스레드가 기다렸다가 한꺼번에 출발한다.
     */
    private fun runConcurrently(times: Int, threads: Int = 32, task: (Int) -> Unit): List<String> {
        val executor = Executors.newFixedThreadPool(threads)
        val startGate = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<String>()
        try {
            val futures = List(times) { i ->
                executor.submit {
                    startGate.await()
                    runCatching { task(i) }.onFailure { failures += it.javaClass.simpleName }
                }
            }
            startGate.countDown()
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        return failures.toList()
    }

    private fun mismatchedAmong(vararg accountNumbers: String): List<String> =
        ledgerAudit.check().mismatchedAccounts.filter { it in accountNumbers }

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
