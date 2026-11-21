package com.coinbank.product

import com.coinbank.account.AccountService
import com.coinbank.product.internal.ProductQuota
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
    private val productQuota: ProductQuota,
) {

    @Transactional
    fun create(name: String, quota: Int): ProductInfo {
        val product = productRepository.save(SavingsProduct(name, quota))
        productQuota.open(checkNotNull(product.id), quota)
        return product.toInfo()
    }

    /**
     * 특판 적금에 가입한다. Redis에서 자리를 하나 가져온 요청만 가입 기록을 남긴다.
     *
     * M3-1에서는 DB에서 가입 수를 세고 비교했다. 세는 것과 넣는 것 사이에 다른 요청이 끼어들어
     * 한도를 넘었다. 이제 "남은 자리 확인 → 하나 빼기"를 Redis가 한 번에 하므로 한도보다 많이 통과할 수 없다.
     * Redis에서 자리를 뺀 뒤 DB 저장이 실패하면 자리 하나가 사라진다. M3-3에서 다룬다.
     */
    @Transactional
    fun subscribe(productId: Long, accountNumber: String): SubscriptionInfo {
        accountService.find(accountNumber) ?: throw NoSuchElementException("계좌를 찾을 수 없습니다: $accountNumber")
        val product = productRepository.findByIdOrNull(productId)
            ?: throw NoSuchElementException("상품을 찾을 수 없습니다: $productId")

        if (!productQuota.take(productId)) throw SoldOutException(productId, product.quota)

        return subscriptionRepository.save(Subscription(productId, accountNumber)).toInfo()
    }

    private fun SavingsProduct.toInfo() = ProductInfo(checkNotNull(id), name, quota)

    private fun Subscription.toInfo() = SubscriptionInfo(checkNotNull(id), productId, accountNumber, createdAt)
}
