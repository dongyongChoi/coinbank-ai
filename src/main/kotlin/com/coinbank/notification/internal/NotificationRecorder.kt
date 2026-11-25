package com.coinbank.notification.internal

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 이벤트 하나를 알림으로 남긴다. 같은 이벤트가 두 번 와도 알림은 한 번만 남긴다(멱등 컨슈머).
 * "처리 표시"와 "알림"을 한 트랜잭션에 넣는다. 알림 저장이 실패하면 처리 표시도 롤백돼서, 다음에 다시 받으면 처리한다.
 */
@Component
class NotificationRecorder(
    private val processedEvents: ProcessedEventRepository,
    private val notifications: NotificationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun record(eventKey: String, vararg toSend: Notification) {
        if (processedEvents.insertIfAbsent(eventKey) == 0) {
            log.debug("이미 처리한 이벤트라 건너뛴다: {}", eventKey)
            return
        }
        notifications.saveAll(toSend.toList())
    }
}
