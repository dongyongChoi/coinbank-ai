package com.coinbank

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@IntegrationTest
class DatabaseConnectionTests(
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `테스트가 MySQL 8_4 컨테이너에 연결된다`() {
        val version = jdbcTemplate.queryForObject("SELECT VERSION()", String::class.java)

        assertNotNull(version)
        assertTrue(version.startsWith("8.4"), "MySQL 버전: $version")
    }
}
