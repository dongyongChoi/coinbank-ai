package com.coinbank.product

import com.coinbank.account.AccountService
import com.coinbank.product.internal.SavingsProduct
import com.coinbank.product.internal.SavingsProductRepository
import com.coinbank.product.internal.Subscription
import com.coinbank.product.internal.SubscriptionRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: SavingsProductRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountService: AccountService,
) {

    @Transactional
    fun create(name: String, quota: Int): ProductInfo =
        productRepository.save(SavingsProduct(name, quota)).toInfo()

    /**
     * 특판 적금에 가입한다. 지금 가입 수를 세고, 한도보다 적으면 가입을 기록한다.
     *
     * M3-1 기준선: 아무것도 잠그지 않는다. "세고 → 비교하고 → 넣는" 사이에 다른 요청이 끼어들 수 있어서
     * 동시에 몰리면 한도를 넘어 개설된다. M3-2에서 Redis 원자적 차감으로 막는다.
     */
    @Transactional
    fun subscribe(productId: Long, accountNumber: String): SubscriptionInfo {
        accountService.find(accountNumber) ?: throw NoSuchElementException("계좌를 찾을 수 없습니다: $accountNumber")
        val product = productRepository.findByIdOrNull(productId)
            ?: throw NoSuchElementException("상품을 찾을 수 없습니다: $productId")

        val subscribed = subscriptionRepository.countByProductId(productId)
        if (subscribed >= product.quota) throw SoldOutException(productId, product.quota)

        return subscriptionRepository.save(Subscription(productId, accountNumber)).toInfo()
    }

    private fun SavingsProduct.toInfo() = ProductInfo(checkNotNull(id), name, quota)

    private fun Subscription.toInfo() = SubscriptionInfo(checkNotNull(id), productId, accountNumber, createdAt)
}
