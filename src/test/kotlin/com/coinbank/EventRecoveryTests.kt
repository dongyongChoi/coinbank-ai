package com.coinbank

import com.coinbank.account.AccountService
import com.coinbank.transfer.TransferService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.ConsumerFactory
import org.testcontainers.kafka.KafkaContainer
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 이벤트를 보내지 못해도 사라지지 않는지 확인한다.
 * Kafka 컨테이너를 잠깐 멈춰서(pause) "대답 없는 Kafka"를 만든다. 멈춘 동안에도 포트는 그대로라 다시 풀면 같은 주소로 붙는다.
 */
@IntegrationTest
@KafkaTest
class EventRecoveryTests(
    @Autowired private val transferService: TransferService,
    @Autowired private val accountService: AccountService,
    @Autowired private val kafkaContainer: KafkaContainer,
    @Autowired private val consumerFactory: ConsumerFactory<String, String>,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `Kafka가 대답하지 않는 동안 끝난 송금의 이벤트도 Kafka가 돌아오면 도착한다`() {
        val from = accountService.open("김코인", 10_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        pauseKafka()
        try {
            // 송금은 Kafka를 기다리지 않고 바로 끝난다. 이벤트는 커밋 뒤에 다른 스레드가 보낸다.
            val started = System.nanoTime()
            transferService.transfer(from, to, 1_000, UUID.randomUUID().toString())
            val tookMillis = (System.nanoTime() - started) / 1_000_000
            assertTrue(tookMillis < 2_000, "Kafka가 멈춰도 송금은 기다리지 않는다 (걸린 시간 ${tookMillis}ms)")

            // Kafka를 기다리는 최대 시간(10초)이 지나도록 못 보낸 채로 남아 있다.
            Thread.sleep(11_000)
            assertEquals(1, incompleteCount(from), "못 보낸 이벤트가 사라지지 않고 남아 있다")
        } finally {
            unpauseKafka()
        }

        // 다시 보내기 일꾼이 Kafka로 보낸다.
        val message = readUntilFound("transfer-completed", from)
        assertTrue("\"toAccountNumber\":\"$to\"" in message, "도착한 이벤트: $message")
        waitUntil("다시 보낸 뒤 완료 표시") { incompleteCount(from) == 0 }
    }

    private fun pauseKafka() {
        kafkaContainer.dockerClient.pauseContainerCmd(kafkaContainer.containerId).exec()
    }

    private fun unpauseKafka() {
        kafkaContainer.dockerClient.unpauseContainerCmd(kafkaContainer.containerId).exec()
    }

    /** 토픽을 처음부터 글자로 읽어 text가 들어 있는 메시지를 찾는다. 다시 보내기를 기다리므로 최대 30초 본다. */
    private fun readUntilFound(topic: String, text: String): String {
        val properties = Properties().apply {
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        }
        consumerFactory.createConsumer("recovery-test-${UUID.randomUUID()}", null, null, properties).use { consumer ->
            consumer.subscribe(listOf(topic))
            repeat(60) {
                consumer.poll(Duration.ofMillis(500)).map { it.value() }.firstOrNull { text in it }?.let { return it }
            }
        }
        throw AssertionError("$topic 토픽에서 '$text'가 들어 있는 메시지를 찾지 못했다")
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("10초 안에 되지 않았다: $what")
    }

    private fun incompleteCount(text: String): Int? =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NULL",
            Int::class.java,
            "%$text%",
        )
}
