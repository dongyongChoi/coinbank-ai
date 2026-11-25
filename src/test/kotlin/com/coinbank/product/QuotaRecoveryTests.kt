package com.coinbank.product

import com.coinbank.IntegrationTest
import com.coinbank.RedisTestcontainersConfiguration
import com.coinbank.account.AccountService
import com.coinbank.product.internal.Subscription
import com.coinbank.product.internal.SubscriptionRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Redis의 남은 자리와 DB의 가입 기록이 어긋나는 경우를 다룬다.
 * SubscriptionRepository를 감싸서(spy) 원하는 때에 저장이 실패하게 만든다. 평소에는 진짜 저장을 한다.
 */
@IntegrationTest
@Import(RedisTestcontainersConfiguration::class)
class QuotaRecoveryTests(
    @Autowired private val productService: ProductService,
    @Autowired private val accountService: AccountService,
    @Autowired private val quotaAudit: QuotaAudit,
    @Autowired private val redis: StringRedisTemplate,
) {

    @MockitoSpyBean private lateinit var subscriptionRepository: SubscriptionRepository

    @Test
    fun `가입 기록 저장이 실패하면 가져간 자리를 돌려준다`() {
        val product = productService.create("특판 적금", quota = 1)
        val first = accountService.open("김코인").accountNumber
        val second = accountService.open("이뱅크").accountNumber

        // 이번 저장만 DB 장애처럼 실패하게 한다. reset으로 흉내를 지우면 다시 진짜로 저장한다.
        doThrow(DataAccessResourceFailureException("DB 장애 흉내"))
            .`when`(subscriptionRepository).save(any(Subscription::class.java))
        assertFailsWith<DataAccessResourceFailureException> { productService.subscribe(product.productId, first) }
        reset(subscriptionRepository)

        // 자리를 돌려받았으므로 한도 1인 상품에 다른 사람이 가입할 수 있다.
        productService.subscribe(product.productId, second)
        assertTrue(quotaAudit.check().mismatches.none { it.productId == product.productId }, "Redis 남은 자리와 가입 기록이 맞는다")
    }

    @Test
    fun `정합성 검사는 Redis 남은 자리와 가입 기록이 어긋난 상품을 찾는다`() {
        val product = productService.create("특판 적금", quota = 5)
        productService.subscribe(product.productId, accountService.open("김코인").accountNumber)

        // 누군가 Redis 숫자를 잘못 고친 상황. 가입 1건이므로 맞는 값은 4다.
        redis.opsForValue().set("product:${product.productId}:remaining", "2")

        val mismatch = quotaAudit.check().mismatches.single { it.productId == product.productId }
        assertEquals(QuotaMismatch(product.productId, remainingInRedis = 2, expectedRemaining = 4), mismatch)
    }
}
