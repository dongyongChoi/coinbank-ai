package com.coinbank.transfer.internal

import com.coinbank.transfer.TransferInfo
import com.coinbank.transfer.TransferService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/transfers")
class TransferController(
    private val transferService: TransferService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun transfer(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: TransferRequest,
    ): TransferInfo =
        transferService.transfer(request.fromAccountNumber, request.toAccountNumber, request.amount, idempotencyKey)
}

data class TransferRequest(
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
)
