package com.coinbank.product.internal

import org.springframework.data.jpa.repository.JpaRepository

interface SavingsProductRepository : JpaRepository<SavingsProduct, Long>
