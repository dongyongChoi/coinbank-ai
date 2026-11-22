package com.coinbank.notification.internal

import java.time.Instant

/*
 * Kafka에서 읽는 이벤트의 모양. transfer·product 모듈의 이벤트 클래스를 가져다 쓰지 않고 따로 적는다.
 * 알림 모듈은 "Kafka에 이런 JSON이 온다"는 약속만 알면 된다. 보내는 쪽이 필드를 더해도
 * 여기서 모르는 필드는 무시하므로(Jackson 3 기본값) 깨지지 않는다.
 */

data class TransferCompletedMessage(
    val transferId: Long,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val completedAt: Instant,
)

data class SubscriptionCreatedMessage(
    val subscriptionId: Long,
    val productId: Long,
    val accountNumber: String,
    val subscribedAt: Instant,
)
