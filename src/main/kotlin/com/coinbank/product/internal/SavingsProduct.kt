package com.coinbank.product.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 선착순 특판 적금 상품. 한도는 만들 때 정하고 바꾸지 않는다. */
@Entity
@Table(name = "savings_product")
class SavingsProduct(
    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, updatable = false)
    val quota: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    init {
        require(name.isNotBlank()) { "상품 이름은 비어 있을 수 없습니다." }
        require(quota > 0) { "한도는 0보다 커야 합니다." }
    }
}
