package com.coinbank.product

import java.time.Instant

/** 다른 모듈과 API 응답에 내보내는 가입 정보. */
data class SubscriptionInfo(
    val subscriptionId: Long,
    val productId: Long,
    val accountNumber: String,
    val createdAt: Instant,
)
