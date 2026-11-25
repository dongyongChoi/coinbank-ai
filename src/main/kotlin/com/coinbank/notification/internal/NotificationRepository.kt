package com.coinbank.notification.internal

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByAccountNumber(accountNumber: String): List<Notification>
}
