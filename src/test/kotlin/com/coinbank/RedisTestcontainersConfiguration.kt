package com.coinbank

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer

/** Redis가 필요한 테스트만 @Import 한다. 다른 테스트는 Redis 컨테이너를 띄우지 않는다. */
@TestConfiguration(proxyBeanMethods = false)
class RedisTestcontainersConfiguration {

    /** 테스트용 Redis를 띄우고, @ServiceConnection이 접속 정보(spring.data.redis.*)를 앱에 넣어 준다. */
    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> = GenericContainer("redis:7").withExposedPorts(6379)
}
