package com.coinbank.account.internal

import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class RedissonConfiguration {

    /**
     * 스타터가 만드는 RedissonClient를 "처음 쓸 때 연결"로 바꾼다. 기본은 앱이 뜰 때 연결이라,
     * 비관 락·낙관 락으로 띄울 때도 Redis가 없으면 앱이 뜨지 못한다.
     */
    @Bean
    fun lazyRedisConnection() = RedissonAutoConfigurationCustomizer { config ->
        config.isLazyInitialization = true
    }
}
