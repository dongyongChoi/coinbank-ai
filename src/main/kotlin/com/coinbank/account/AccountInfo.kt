package com.coinbank.account

import java.time.Instant

/** 다른 모듈과 API 응답에 내보내는 계좌 정보. 엔티티는 모듈 밖으로 나가지 않는다. */
data class AccountInfo(
    val accountNumber: String,
    val ownerName: String,
    val balance: Long,
    val createdAt: Instant,
)
