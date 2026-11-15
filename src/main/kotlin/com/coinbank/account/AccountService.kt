package com.coinbank.account

import com.coinbank.account.internal.Account
import com.coinbank.account.internal.AccountRepository
import com.coinbank.account.internal.LedgerEntry
import com.coinbank.account.internal.LedgerEntryRepository
import com.coinbank.account.internal.LockStrategy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.random.Random

@Service
@Transactional(readOnly = true)
class AccountService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    @Value("\${coinbank.lock-strategy}") private val lockStrategy: LockStrategy,
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

    /**
     * 한 계좌에서 다른 계좌로 돈을 옮긴다. 두 계좌의 잔액 컬럼을 바꾸고
     * 같은 트랜잭션에서 원장에 음수 한 줄, 양수 한 줄을 남긴다.
     */
    @Transactional
    fun moveMoney(fromAccountNumber: String, toAccountNumber: String, amount: Long, transactionId: String) {
        require(fromAccountNumber != toAccountNumber) { "같은 계좌로는 옮길 수 없습니다." }

        // 방향과 상관없이 항상 계좌 번호가 작은 쪽부터 읽는다. 순서가 같으면 서로를 기다리는 데드락이 생기지 않는다.
        // 낙관 락에서도 UPDATE가 읽은 순서대로 나가므로 같은 규칙이 필요하다.
        val accounts = listOf(fromAccountNumber, toAccountNumber)
            .sorted()
            .associateWith { getAccountForUpdate(it) }

        accounts.getValue(fromAccountNumber).withdraw(amount)
        accounts.getValue(toAccountNumber).deposit(amount)
        record(transactionId, fromAccountNumber, -amount)
        record(transactionId, toAccountNumber, amount)
    }

    private fun record(transactionId: String, accountNumber: String, amount: Long) {
        ledgerEntryRepository.save(LedgerEntry(transactionId, accountNumber, amount))
    }

    private fun getAccountForUpdate(accountNumber: String): Account =
        when (lockStrategy) {
            LockStrategy.PESSIMISTIC -> accountRepository.findWithLockByAccountNumber(accountNumber)
            LockStrategy.OPTIMISTIC -> accountRepository.findByAccountNumber(accountNumber)
        } ?: throw NoSuchElementException("계좌를 찾을 수 없습니다: $accountNumber")

    /** "3333" + 무작위 8자리. 이미 있는 번호면 다시 뽑는다. */
    private fun newAccountNumber(): String =
        generateSequence { "3333%08d".format(Random.nextInt(100_000_000)) }
            .first { !accountRepository.existsByAccountNumber(it) }

    private fun Account.toInfo() = AccountInfo(accountNumber, ownerName, balance, createdAt)
}
