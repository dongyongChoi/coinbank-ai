package com.coinbank.product

import com.coinbank.IntegrationTest
import com.coinbank.RedisTestcontainersConfiguration
import com.coinbank.account.AccountService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * 선착순 특판에 가입이 한꺼번에 몰려도 한도보다 많이 개설되지 않는지 확인한다.
 * HTTP를 거치지 않고 ProductService를 직접 부른다. 보려는 것이 트랜잭션끼리의 경쟁이기 때문이다.
 * 남은 자리를 Redis가 세므로 Redis 컨테이너도 함께 띄운다.
 */
@IntegrationTest
@Import(RedisTestcontainersConfiguration::class)
class ConcurrentSubscriptionTests(
    @Autowired private val productService: ProductService,
    @Autowired private val accountService: AccountService,
    @Autowired private val quotaAudit: QuotaAudit,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    /**
     * 한 번의 몰림으로는 경쟁이 늘 드러나지 않는다(락 없는 기준선에서 10번 중 4번만 초과 개설, 2026-11-21).
     * 그래서 같은 몰림을 상품 10개에 차례로 일으키고, 상품마다 개설 수를 모아 한꺼번에 확인한다.
     * 락 없는 기준선(M3-1)에서는 상품 10개 중 4~5개가 11개로 초과 개설됐다.
     */
    @Test
    fun `한도 10개 상품 10개에 각각 100명이 동시에 가입해도 상품마다 10개만 개설된다`() {
        val accounts = List(100) { accountService.open("고객$it").accountNumber }
        val products = List(10) { productService.create("특판 적금 $it", quota = 10) }

        val results = products.map { product ->
            val failures = runConcurrently(times = 100) { i ->
                try {
                    productService.subscribe(product.productId, accounts[i])
                } catch (_: SoldOutException) {
                    // 마감으로 거절된 가입. 기대한 결과다.
                }
            }
            assertEquals(emptyList(), failures, "마감 말고 다른 이유로 실패한 가입")
            subscriptionCountInDb(product.productId)
        }

        assertEquals(List(10) { 10 }, results, "상품마다 개설된 가입 수")
        val productIds = products.map { it.productId }
        assertEquals(
            emptyList(),
            quotaAudit.check().mismatches.filter { it.productId in productIds },
            "Redis 남은 자리와 가입 기록이 어긋난 상품",
        )
    }

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

    private fun subscriptionCountInDb(productId: Long): Int? =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription WHERE product_id = ?", Int::class.java, productId)
}
