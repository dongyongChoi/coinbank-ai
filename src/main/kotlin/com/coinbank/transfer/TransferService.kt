package com.coinbank.transfer

import com.coinbank.account.AccountLock
import com.coinbank.account.AccountService
import com.coinbank.transfer.internal.Transfer
import com.coinbank.transfer.internal.TransferRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import kotlin.random.Random

@Service
class TransferService(
    private val accountService: AccountService,
    private val accountLock: AccountLock,
    private val transferRepository: TransferRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 송금한다. 같은 idempotencyKey로 이미 끝난 송금이 있으면 다시 하지 않고 그 결과를 돌려준다.
     * 낙관 락 충돌로 실패하면 잠깐 쉬고 새 트랜잭션으로 다시 시도한다.
     * 재시도는 트랜잭션 밖에서 한다. 충돌한 트랜잭션은 이미 롤백돼서 그 안에서는 다시 할 수 없다.
     */
    fun transfer(
        fromAccountNumber: String,
        toAccountNumber: String,
        amount: Long,
        idempotencyKey: String,
    ): TransferInfo {
        val request = Transfer(fromAccountNumber, toAccountNumber, amount, idempotencyKey)
        findCompleted(request)?.let { return it }

        var attempt = 1
        while (true) {
            try {
                // 락을 먼저 잡고 그 안에서 트랜잭션을 열고 닫는다. 커밋이 끝난 뒤에 락이 풀려야 한다.
                return accountLock.withLock(listOf(fromAccountNumber, toAccountNumber)) {
                    transactionTemplate.execute {
                        transferOnce(fromAccountNumber, toAccountNumber, amount, idempotencyKey)
                    }
                }
            } catch (e: DataIntegrityViolationException) {
                // 같은 키의 요청이 동시에 와서 다른 쪽이 먼저 기록했다. 유니크 제약이 이쪽 INSERT를 막았고
                // 트랜잭션은 롤백됐다. 먼저 끝난 송금의 결과를 돌려준다.
                log.debug("같은 Idempotency-Key의 요청이 먼저 기록됐다. 그 결과를 돌려준다")
                return findCompleted(request) ?: throw e
            } catch (e: OptimisticLockingFailureException) {
                if (attempt >= MAX_ATTEMPTS) throw e
                log.debug("낙관 락 충돌. 다시 시도한다 ({}번째 시도 실패)", attempt)
                // 충돌한 요청들이 같은 순간에 다시 몰리지 않게 쉬는 시간을 무작위로 흩는다.
                Thread.sleep(Random.nextLong(1, MAX_BACKOFF_MILLIS))
                attempt++
            }
        }
    }

    /**
     * 송금 기록과 돈 옮기기. 한 트랜잭션 안에서 실행되고, 하나라도 실패하면 전부 롤백된다.
     * 기록을 먼저 넣는다. 같은 키의 요청이 동시에 오면 유니크 제약에 걸려 돈을 옮기기 전에 멈춘다.
     */
    private fun transferOnce(
        fromAccountNumber: String,
        toAccountNumber: String,
        amount: Long,
        idempotencyKey: String,
    ): TransferInfo {
        val transfer = transferRepository.save(Transfer(fromAccountNumber, toAccountNumber, amount, idempotencyKey))

        accountService.moveMoney(fromAccountNumber, toAccountNumber, amount, transfer.transactionId)

        return transfer.toInfo()
    }

    /** 같은 키로 이미 끝난 송금을 찾는다. 키는 같은데 내용이 다르면 잘못된 요청이다. */
    private fun findCompleted(request: Transfer): TransferInfo? {
        val completed = transferRepository.findByIdempotencyKey(request.idempotencyKey) ?: return null
        require(
            completed.fromAccountNumber == request.fromAccountNumber &&
                completed.toAccountNumber == request.toAccountNumber &&
                completed.amount == request.amount,
        ) { "같은 Idempotency-Key로 내용이 다른 요청이 왔습니다." }
        return completed.toInfo()
    }

    private fun Transfer.toInfo() =
        TransferInfo(checkNotNull(id), fromAccountNumber, toAccountNumber, amount, createdAt)

    companion object {
        /**
         * 무한 반복을 막는 안전 상한. 한 계좌에 100건이 몰린 테스트에서
         * 송금 한 건이 많게는 31번째 시도에 성공했다 (2026-11-15 측정).
         */
        private const val MAX_ATTEMPTS = 100
        private const val MAX_BACKOFF_MILLIS = 20L
    }
}
