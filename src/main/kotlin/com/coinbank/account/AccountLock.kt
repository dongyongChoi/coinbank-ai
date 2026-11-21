package com.coinbank.account

import com.coinbank.account.internal.LockStrategy
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.CannotAcquireLockException
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 트랜잭션 바깥에서 계좌를 잠근다. Redis 분산 락 전략일 때만 실제로 잠그고,
 * 비관 락·낙관 락 전략에서는 DB가 보호하므로 action을 그대로 실행한다.
 */
@Component
class AccountLock(
    private val redissonClient: RedissonClient,
    @Value("\${coinbank.lock-strategy}") private val lockStrategy: LockStrategy,
) {

    /** 계좌들을 잠근 채로 action을 실행하고, 끝나면 락을 푼다. action 안에서 트랜잭션을 열고 닫아야 한다. */
    fun <T> withLock(accountNumbers: List<String>, action: () -> T): T {
        if (lockStrategy != LockStrategy.REDIS) return action()

        // 계좌마다 Redis 키 하나가 락이다. 여러 개를 한 묶음으로 잡고, 순서는 계좌 번호 순으로 맞춘다.
        val locks = accountNumbers.sorted().map { redissonClient.getLock("lock:account:$it") }
        val lock = redissonClient.getMultiLock(*locks.toTypedArray())

        if (!lock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw CannotAcquireLockException("계좌 락을 ${WAIT_SECONDS}초 안에 얻지 못했습니다: $accountNumbers")
        }
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private const val WAIT_SECONDS = 10L
    }
}
