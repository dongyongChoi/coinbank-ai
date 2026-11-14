package com.coinbank

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    /** 테스트용 MySQL을 띄우고, @ServiceConnection이 접속 정보를 앱에 넣어 준다. */
    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer = MySQLContainer("mysql:8.4")
}
