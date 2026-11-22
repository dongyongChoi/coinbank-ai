package com.coinbank.transfer

import org.springframework.modulith.events.Externalized
import java.time.Instant

/**
 * 송금이 끝났다는 이벤트. 송금 기록과 같은 트랜잭션에서 발행되고, Modulith가 event_publication 테이블에 함께 적는다.
 * 커밋이 끝나면 Modulith가 Kafka의 transfer-completed 토픽으로 보낸다. 송금이 롤백되면 이벤트도 함께 사라진다.
 * 다른 모듈과 다른 서비스(M5)가 읽는 약속이라, 필드를 지우거나 이름을 바꾸지 않는다.
 */
@Externalized("transfer-completed")
data class TransferCompleted(
    val transferId: Long,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val completedAt: Instant,
)
