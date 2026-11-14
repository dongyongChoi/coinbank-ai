package com.coinbank

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CoinbankApplication

fun main(args: Array<String>) {
    runApplication<CoinbankApplication>(*args)
}
