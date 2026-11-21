package com.coinbank.product.internal

import com.coinbank.product.ProductInfo
import com.coinbank.product.ProductService
import com.coinbank.product.SubscriptionInfo
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/products")
class ProductController(
    private val productService: ProductService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateProductRequest): ProductInfo =
        productService.create(request.name, request.quota)

    @PostMapping("/{productId}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    fun subscribe(@PathVariable productId: Long, @RequestBody request: SubscribeRequest): SubscriptionInfo =
        productService.subscribe(productId, request.accountNumber)
}

data class CreateProductRequest(
    val name: String,
    val quota: Int,
)

data class SubscribeRequest(
    val accountNumber: String,
)
