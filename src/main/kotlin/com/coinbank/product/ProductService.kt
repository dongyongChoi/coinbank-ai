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
import org.springframework.transaction.support.TransactionTemplate

@Service
class ProductService(
    private val productRepository: SavingsProductRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountService: AccountService,
    private val productQuota: ProductQuota,
    private val transactionTemplate: TransactionTemplate,
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
     *
     * 자리를 가져온 뒤 가입 기록을 남기지 못하면 자리를 돌려준다. 그래서 트랜잭션을 메서드 전체에 걸지 않고
     * 기록하는 부분에만 건다. 커밋까지 끝난 뒤에 성공인지 실패인지 알 수 있어야 돌려줄지 정할 수 있다.
     */
    fun subscribe(productId: Long, accountNumber: String): SubscriptionInfo {
        accountService.find(accountNumber) ?: throw NoSuchElementException("계좌를 찾을 수 없습니다: $accountNumber")
        val product = productRepository.findByIdOrNull(productId)
            ?: throw NoSuchElementException("상품을 찾을 수 없습니다: $productId")

        if (!productQuota.take(productId)) throw SoldOutException(productId, product.quota)

        try {
            return checkNotNull(
                transactionTemplate.execute {
                    subscriptionRepository.save(Subscription(productId, accountNumber)).toInfo()
                },
            )
        } catch (e: Exception) {
            // 기록이 롤백됐으니 가져간 자리도 돌려준다.
            // 드물게 커밋은 됐는데 응답만 끊겨 여기로 올 수 있다. 그러면 자리가 하나 늘어난다. QuotaAudit이 찾아낸다.
            productQuota.release(productId)
            throw e
        }
    }

    private fun SavingsProduct.toInfo() = ProductInfo(checkNotNull(id), name, quota)

    private fun Subscription.toInfo() = SubscriptionInfo(checkNotNull(id), productId, accountNumber, createdAt)
}
