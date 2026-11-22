package com.coinbank.product

import org.springframework.modulith.events.Externalized
import java.time.Instant

/**
 * 특판 적금에 가입했다는 이벤트. 가입 기록과 같은 트랜잭션에서 발행되고, Modulith가 event_publication 테이블에 함께 적는다.
 * 커밋이 끝나면 Modulith가 Kafka의 subscription-created 토픽으로 보낸다.
 * 다른 모듈과 다른 서비스(M5)가 읽는 약속이라, 필드를 지우거나 이름을 바꾸지 않는다.
 */
@Externalized("subscription-created")
data class SubscriptionCreated(
    val subscriptionId: Long,
    val productId: Long,
    val accountNumber: String,
    val subscribedAt: Instant,
)
