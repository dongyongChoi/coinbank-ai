package com.coinbank.transfer

import java.time.Instant

data class TransferInfo(
    val transferId: Long,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val createdAt: Instant,
)
