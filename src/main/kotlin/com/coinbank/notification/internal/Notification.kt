package com.coinbank.notification.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 계좌 주인에게 보낸 알림 한 건. 실제 푸시·문자 발송은 이 프로젝트 범위 밖이라, 알림함에 남기는 것까지 한다. */
@Entity
@Table(name = "notification", indexes = [Index(columnList = "accountNumber")])
class Notification(
    @Column(nullable = false, updatable = false, length = 12)
    val accountNumber: String,

    @Column(nullable = false, updatable = false, length = 200)
    val message: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    // MySQL DATETIME(6)는 마이크로초까지만 저장한다.
    val createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
}
