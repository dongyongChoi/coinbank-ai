package com.coinbank.product

/** 선착순 한도가 다 차서 더 가입할 수 없다. */
class SoldOutException(
    val productId: Long,
    val quota: Int,
) : RuntimeException("마감된 상품입니다. 상품: $productId, 한도: $quota")
