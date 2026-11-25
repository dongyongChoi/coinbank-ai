package com.coinbank

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.kafka.KafkaContainer

/** Kafka가 필요한 테스트만 @Import 한다. 다른 테스트는 Kafka 컨테이너를 띄우지 않는다. */
@TestConfiguration(proxyBeanMethods = false)
class KafkaTestcontainersConfiguration {

    /** docker-compose와 같은 이미지로 테스트용 Kafka를 띄운다. 접속 주소(spring.kafka.bootstrap-servers)는 @ServiceConnection이 넣어 준다. */
    @Bean
    @ServiceConnection
    fun kafkaContainer(): KafkaContainer = KafkaContainer("apache/kafka:4.3.0")
}
