package com.coinbank

import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * Kafka가 필요한 통합 테스트에 @IntegrationTest와 함께 붙인다.
 * 테스트용 Kafka 컨테이너를 띄우고, @IntegrationTest가 꺼 둔 이벤트의 Kafka 발행과 리스너를 다시 켠다.
 * 못 보낸 이벤트를 다시 보내는 주기와 기준은 테스트가 오래 기다리지 않게 짧게 줄인다.
 * @TestPropertySource는 @SpringBootTest(properties)보다 우선한다.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(KafkaTestcontainersConfiguration::class)
@TestPropertySource(
    properties = [
        "spring.modulith.events.externalization.enabled=true",
        "spring.kafka.listener.auto-startup=true",
        "coinbank.events.resubmit-interval=1s",
        "coinbank.events.resubmit-older-than=12s",
    ],
)
annotation class KafkaTest
