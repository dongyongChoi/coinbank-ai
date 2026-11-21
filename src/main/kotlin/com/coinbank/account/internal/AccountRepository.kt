package com.coinbank.account.internal

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface AccountRepository : JpaRepository<Account, Long> {

    fun findByAccountNumber(accountNumber: String): Account?

    /**
     * 비관 락. SELECT ... FOR UPDATE로 읽어서, 이 트랜잭션이 끝날 때까지
     * 다른 트랜잭션이 같은 계좌 행을 잠그거나 바꾸지 못하게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByAccountNumber(accountNumber: String): Account?

    fun existsByAccountNumber(accountNumber: String): Boolean
}
