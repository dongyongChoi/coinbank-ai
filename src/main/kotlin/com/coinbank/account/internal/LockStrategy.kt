package com.coinbank.account.internal

/** 송금할 때 계좌를 동시 갱신에서 보호하는 방식. application.yml의 coinbank.lock-strategy로 고른다. */
enum class LockStrategy {
    /** 비관 락: 읽을 때 행을 잠근다 (SELECT ... FOR UPDATE). */
    PESSIMISTIC,

    /** 낙관 락: 잠그지 않고 읽고, 저장할 때 version이 그대로인지 확인한다. */
    OPTIMISTIC,
}
