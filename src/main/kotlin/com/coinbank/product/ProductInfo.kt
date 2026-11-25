package com.coinbank.product

/** 다른 모듈과 API 응답에 내보내는 특판 상품 정보. */
data class ProductInfo(
    val productId: Long,
    val name: String,
    /** 선착순으로 받을 수 있는 가입 수. */
    val quota: Int,
)
