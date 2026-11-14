package com.coinbank.account.internal

import com.coinbank.account.AccountInfo
import com.coinbank.account.AccountService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/accounts")
class AccountController(
    private val accountService: AccountService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun open(@RequestBody request: OpenAccountRequest): AccountInfo =
        accountService.open(request.ownerName, request.initialBalance)

    @GetMapping("/{accountNumber}")
    fun get(@PathVariable accountNumber: String): AccountInfo =
        accountService.find(accountNumber)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다: $accountNumber")
}

data class OpenAccountRequest(
    val ownerName: String,
    val initialBalance: Long = 0,
)
