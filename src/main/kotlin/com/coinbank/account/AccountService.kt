package com.coinbank.account

import com.coinbank.account.internal.Account
import com.coinbank.account.internal.AccountRepository
import com.coinbank.account.internal.BalanceModel
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
    @Value("\${coinbank.balance-model}") private val balanceModel: BalanceModel,
) {

    init {
        // 낙관 락은 잔액 컬럼이 바뀔 때 오르는 version으로 충돌을 알아챈다. B안은 컬럼을 고치지 않아서
        // version이 그대로이고, 동시 출금이 둘 다 통과해 잔액이 음수가 될 수 있다. 그래서 함께 쓰지 못하게 막는다.
        require(!(balanceModel == BalanceModel.LEDGER_ONLY && lockStrategy == LockStrategy.OPTIMISTIC)) {
            "coinbank.balance-model=ledger-only는 낙관 락과 함께 쓸 수 없습니다."
        }
    }

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

    /** 지금 잔액. C안은 잔액 컬럼, B안은 원장 합산이다. */
    private fun balanceOf(account: Account): Long =
        when (balanceModel) {
            BalanceModel.LEDGER_AND_COLUMN -> account.balance
            BalanceModel.LEDGER_ONLY -> ledgerEntryRepository.sumAmountByAccountNumber(account.accountNumber)
        }

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

        when (balanceModel) {
            BalanceModel.LEDGER_AND_COLUMN -> {
                accounts.getValue(fromAccountNumber).withdraw(amount)
                accounts.getValue(toAccountNumber).deposit(amount)
            }
            // 잔액 컬럼은 고치지 않는다. 계좌 행은 위에서 잠갔으므로, 합산하는 동안 같은 계좌의 다른 송금은 기다린다.
            BalanceModel.LEDGER_ONLY -> {
                require(amount > 0) { "금액은 0보다 커야 합니다." }
                val balance = balanceOf(accounts.getValue(fromAccountNumber))
                if (balance < amount) throw InsufficientBalanceException(fromAccountNumber, balance, amount)
            }
        }
        record(transactionId, fromAccountNumber, -amount)
        record(transactionId, toAccountNumber, amount)
    }

    private fun record(transactionId: String, accountNumber: String, amount: Long) {
        ledgerEntryRepository.save(LedgerEntry(transactionId, accountNumber, amount))
    }

    private fun getAccountForUpdate(accountNumber: String): Account =
        when (lockStrategy) {
            LockStrategy.PESSIMISTIC -> accountRepository.findWithLockByAccountNumber(accountNumber)
            // REDIS는 AccountLock이 트랜잭션 밖에서 이미 잠갔으므로 DB 행은 잠그지 않는다.
            LockStrategy.OPTIMISTIC, LockStrategy.REDIS -> accountRepository.findByAccountNumber(accountNumber)
        } ?: throw NoSuchElementException("계좌를 찾을 수 없습니다: $accountNumber")

    /** "3333" + 무작위 8자리. 이미 있는 번호면 다시 뽑는다. */
    private fun newAccountNumber(): String =
        generateSequence { "3333%08d".format(Random.nextInt(100_000_000)) }
            .first { !accountRepository.existsByAccountNumber(it) }

    private fun Account.toInfo() = AccountInfo(accountNumber, ownerName, balanceOf(this), createdAt)
}
