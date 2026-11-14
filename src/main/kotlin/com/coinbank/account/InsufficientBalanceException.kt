package com.coinbank.account

class InsufficientBalanceException(
    val accountNumber: String,
    val balance: Long,
    val amount: Long,
) : RuntimeException("잔액이 부족합니다. 계좌: $accountNumber, 잔액: $balance, 요청 금액: $amount")
