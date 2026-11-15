package com.coinbank.account

import com.coinbank.account.internal.Account
import com.coinbank.account.internal.AccountRepository
import com.coinbank.account.internal.LedgerEntry
import com.coinbank.account.internal.LedgerEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.random.Random

@Service
@Transactional(readOnly = true)
class AccountService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
) {

    @Transactional
    fun open(ownerName: String, initialBalance: Long = 0): AccountInfo {
        val account = accountRepository.save(Account(newAccountNumber(), ownerName, initialBalance))
        if (initialBalance > 0) {
            // 초기 잔액도 거래다. 은행 바깥(EXTERNAL)에서 이 계좌로 들어온 것으로 적는다.
            val transactionId = UUID.randomUUID().toString()
            record(transactionId, LedgerEntry.EXTERNAL, -initialBalance)
            record(transactionId, account.accountNumber, initialBalance)
        }
        return account.toInfo()
    }

    fun find(accountNumber: String): AccountInfo? =
        accountRepository.findByAccountNumber(accountNumber)?.toInfo()

    /** 잔액 컬럼을 줄이고 같은 트랜잭션에서 원장에 음수 한 줄을 남긴다. */
    @Transactional
    fun withdraw(accountNumber: String, amount: Long, transactionId: String) {
        getAccount(accountNumber).withdraw(amount)
        record(transactionId, accountNumber, -amount)
    }

    /** 잔액 컬럼을 늘리고 같은 트랜잭션에서 원장에 양수 한 줄을 남긴다. */
    @Transactional
    fun deposit(accountNumber: String, amount: Long, transactionId: String) {
        getAccount(accountNumber).deposit(amount)
        record(transactionId, accountNumber, amount)
    }

    private fun record(transactionId: String, accountNumber: String, amount: Long) {
        ledgerEntryRepository.save(LedgerEntry(transactionId, accountNumber, amount))
    }

    private fun getAccount(accountNumber: String): Account =
        accountRepository.findByAccountNumber(accountNumber)
            ?: throw NoSuchElementException("계좌를 찾을 수 없습니다: $accountNumber")

    /** "3333" + 무작위 8자리. 이미 있는 번호면 다시 뽑는다. */
    private fun newAccountNumber(): String =
        generateSequence { "3333%08d".format(Random.nextInt(100_000_000)) }
            .first { !accountRepository.existsByAccountNumber(it) }

    private fun Account.toInfo() = AccountInfo(accountNumber, ownerName, balance, createdAt)
}
