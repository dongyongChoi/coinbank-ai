package com.coinbank

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals

/**
 * 앱이 Kafka에 메시지를 보내고 다시 읽을 수 있는지 확인한다.
 * 스프링 부트가 만들어 준 KafkaTemplate(보내기)과 ConsumerFactory(읽기)를 그대로 쓴다.
 */
@IntegrationTest
@Import(KafkaTestcontainersConfiguration::class)
class KafkaConnectionTests(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
    @Autowired private val consumerFactory: ConsumerFactory<String, String>,
) {

    @Test
    fun `보낸 메시지를 같은 토픽에서 다시 읽는다`() {
        // 테스트마다 새 토픽. Kafka는 처음 보내는 토픽을 알아서 만든다.
        val topic = "connection-check-${UUID.randomUUID()}"

        // get()으로 Kafka가 "받았다"고 답할 때까지 기다린다. 기다리지 않으면 보내기 실패를 모르고 지나간다.
        kafkaTemplate.send(topic, "key-1", "안녕, Kafka").get()

        // 읽는 쪽은 토픽의 처음부터 읽는다. 기본값(latest)이면 이미 와 있는 메시지를 건너뛴다.
        val properties = Properties().apply { put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest") }
        consumerFactory.createConsumer("connection-check", null, null, properties).use { consumer ->
            consumer.subscribe(listOf(topic))
            val records = generateSequence { consumer.poll(Duration.ofMillis(500)) }
                .take(20)
                .flatMap { it.toList() }
                .take(1)
                .toList()

            assertEquals(listOf("key-1" to "안녕, Kafka"), records.map { it.key() to it.value() })
        }
    }
}
