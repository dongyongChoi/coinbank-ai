package com.coinbank.account.internal

/** 잔액을 어디서 읽는가 (ADR 001). application.yml의 coinbank.balance-model로 고른다. */
enum class BalanceModel {
    /** C안: 원장을 쌓으면서 잔액 컬럼도 같은 트랜잭션에서 고친다. 잔액은 컬럼에서 읽는다. */
    LEDGER_AND_COLUMN,

    /**
     * B안: 원장만 쌓는다. 잔액은 매번 원장을 합산해서 구한다.
     * 잔액 컬럼은 계좌 개설 때 값 그대로 남고 쓰지 않는다.
     */
    LEDGER_ONLY,
}
