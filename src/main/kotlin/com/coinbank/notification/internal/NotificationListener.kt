package com.coinbank.notification.internal

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.util.Locale

/**
 * Kafka 토픽에서 이벤트를 읽어 알림을 만든다. JSON을 객체로 바꾸는 일은 Modulith가 등록한 변환기가 한다.
 * 메서드가 예외 없이 끝나야 Kafka에 "여기까지 읽었다"(오프셋)를 적는다. 그래서 중간에 죽으면 같은 이벤트를 다시 받는다.
 */
@Component
class NotificationListener(
    private val recorder: NotificationRecorder,
) {

    @KafkaListener(topics = ["transfer-completed"], groupId = "notification")
    fun onTransferCompleted(event: TransferCompletedMessage) {
        val amount = won(event.amount)
        recorder.record(
            "transfer-completed:${event.transferId}",
            Notification(event.fromAccountNumber, "$amount 출금 (받는 계좌 ${event.toAccountNumber})"),
            Notification(event.toAccountNumber, "$amount 입금 (보낸 계좌 ${event.fromAccountNumber})"),
        )
    }

    @KafkaListener(topics = ["subscription-created"], groupId = "notification")
    fun onSubscriptionCreated(event: SubscriptionCreatedMessage) {
        recorder.record(
            "subscription-created:${event.subscriptionId}",
            Notification(event.accountNumber, "특판 적금 가입 완료 (상품 ${event.productId})"),
        )
    }

    private fun won(amount: Long) = NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
}
