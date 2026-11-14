package com.coinbank.transfer

import com.coinbank.account.AccountService
import com.coinbank.transfer.internal.Transfer
import com.coinbank.transfer.internal.TransferRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransferService(
    private val accountService: AccountService,
    private val transferRepository: TransferRepository,
) {

    /** 출금, 입금, 기록을 한 트랜잭션으로 묶는다. 하나라도 실패하면 전부 롤백된다. */
    @Transactional
    fun transfer(fromAccountNumber: String, toAccountNumber: String, amount: Long): TransferInfo {
        val transfer = Transfer(fromAccountNumber, toAccountNumber, amount)

        accountService.withdraw(fromAccountNumber, amount)
        accountService.deposit(toAccountNumber, amount)

        return transferRepository.save(transfer).toInfo()
    }

    private fun Transfer.toInfo() =
        TransferInfo(checkNotNull(id), fromAccountNumber, toAccountNumber, amount, createdAt)
}
