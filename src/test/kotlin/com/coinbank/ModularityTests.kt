package com.coinbank

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import kotlin.test.assertEquals

class ModularityTests {

    private val modules = ApplicationModules.of(CoinbankApplication::class.java)

    @Test
    fun `모듈 경계를 지킨다`() {
        modules.verify()
    }

    @Test
    fun `계획한 모듈 다섯 개가 인식된다`() {
        val names = modules.map { it.identifier.toString() }.sorted()

        assertEquals(listOf("account", "home", "notification", "product", "transfer"), names)
    }
}
