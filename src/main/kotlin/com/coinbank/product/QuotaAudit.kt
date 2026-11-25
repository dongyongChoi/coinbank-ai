package com.coinbank.product

import com.coinbank.product.internal.ProductQuota
import com.coinbank.product.internal.SavingsProductRepository
import com.coinbank.product.internal.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Redis의 남은 자리와 DB의 가입 기록이 맞는지 확인한다. 테스트와 부하 테스트 뒤의 정합성 확인에 쓴다.
 * 맞는 상태는 "Redis 남은 자리 = 한도 - 가입 기록 수"다.
 * 가입이 진행 중이면 Redis에서는 빠졌는데 DB에는 아직 없는 순간이 있으므로, 요청이 멈춘 뒤에 돌린다.
 */
@Service
class QuotaAudit(
    private val productRepository: SavingsProductRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val productQuota: ProductQuota,
) {

    /** 모든 상품을 하나씩 확인한다. 특판 상품은 몇 개 되지 않아서 한 번에 다 읽는다. */
    @Transactional(readOnly = true)
    fun check() = QuotaAuditResult(
        mismatches = productRepository.findAll().mapNotNull { product ->
            val productId = checkNotNull(product.id)
            val expected = product.quota - subscriptionRepository.countByProductId(productId)
            val inRedis = productQuota.remaining(productId)
            if (inRedis == expected) null else QuotaMismatch(productId, inRedis, expected)
        },
    )
}

data class QuotaAuditResult(
    val mismatches: List<QuotaMismatch>,
) {
    val isConsistent: Boolean
        get() = mismatches.isEmpty()
}

data class QuotaMismatch(
    val productId: Long,
    /** Redis에 적힌 남은 자리. 숫자가 아예 없으면 null. */
    val remainingInRedis: Long?,
    /** 가입 기록으로 계산한 남은 자리 (한도 - 가입 수). */
    val expectedRemaining: Long,
)
