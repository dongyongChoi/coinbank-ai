package com.coinbank.notification.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 이미 처리한 이벤트의 표시. 키가 기본 키라서 같은 이벤트는 한 줄만 들어갈 수 있다.
 * 키는 "토픽:업무 ID"다(예: transfer-completed:42). 같은 이벤트를 다시 보내도 업무 ID는 그대로라 키가 같다.
 */
@Entity
@Table(name = "processed_event")
class ProcessedEvent(
    @Id
    @Column(length = 100)
    val eventKey: String,
) {
    @Column(nullable = false, updatable = false)
    val processedAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
}
