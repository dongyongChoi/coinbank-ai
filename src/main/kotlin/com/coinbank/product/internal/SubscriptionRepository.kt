package com.coinbank.product.internal

import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionRepository : JpaRepository<Subscription, Long> {

    fun countByProductId(productId: Long): Long
}
