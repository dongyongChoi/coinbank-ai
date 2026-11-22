package com.coinbank

import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * Kafka가 필요한 통합 테스트에 @IntegrationTest와 함께 붙인다.
 * 테스트용 Kafka 컨테이너를 띄우고, @IntegrationTest가 꺼 둔 이벤트의 Kafka 발행을 다시 켠다.
 * @TestPropertySource는 @SpringBootTest(properties)보다 우선한다.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(KafkaTestcontainersConfiguration::class)
@TestPropertySource(properties = ["spring.modulith.events.externalization.enabled=true"])
annotation class KafkaTest
