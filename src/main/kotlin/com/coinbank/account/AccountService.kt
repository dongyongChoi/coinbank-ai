package com.coinbank.account

import com.coinbank.account.internal.Account
import com.coinbank.account.internal.AccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

@Service
@Transactional(readOnly = true)
class AccountService(
    private val accountRepository: AccountRepository,
) {

    @Transactional
    fun open(ownerName: String, initialBalance: Long = 0): AccountInfo {
        val account = Account(newAccountNumber(), ownerName, initialBalance)
        return accountRepository.save(account).toInfo()
    }

    fun find(accountNumber: String): AccountInfo? =
        accountRepository.findByAccountNumber(accountNumber)?.toInfo()

    /** "3333" + 무작위 8자리. 이미 있는 번호면 다시 뽑는다. */
    private fun newAccountNumber(): String =
        generateSequence { "3333%08d".format(Random.nextInt(100_000_000)) }
            .first { !accountRepository.existsByAccountNumber(it) }

    private fun Account.toInfo() = AccountInfo(accountNumber, ownerName, balance, createdAt)
}
