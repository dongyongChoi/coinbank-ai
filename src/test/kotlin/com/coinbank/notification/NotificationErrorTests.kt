package com.coinbank.notification

import com.coinbank.IntegrationTest
import com.coinbank.KafkaTest
import com.coinbank.notification.internal.Notification
import com.coinbank.notification.internal.NotificationRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 알림을 남기지 못한 이벤트가 사라지지 않고 DLT로 옮겨지는지 확인한다.
 * 테스트가 오래 걸리지 않게 다시 시도는 2번, 쉬는 시간은 0.1초부터로 줄인다.
 */
@IntegrationTest
@KafkaTest
@TestPropertySource(
    properties = [
        "coinbank.notification.retry.max-retries=2",
        "coinbank.notification.retry.initial-interval=100ms",
        "coinbank.notification.retry.max-interval=200ms",
    ],
)
class NotificationErrorTests(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, ByteArray>,
    @Autowired private val consumerFactory: ConsumerFactory<String, String>,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    /** 알림 저장을 원하는 때에 실패하게 만든다. 평소에는 진짜로 저장한다. */
    @MockitoSpyBean private lateinit var notifications: NotificationRepository

    @Test
    fun `알림 저장이 계속 실패하면 몇 번 다시 시도한 뒤 DLT로 옮긴다`() {
        doThrow(DataAccessResourceFailureException("DB 장애 흉내"))
            .`when`(notifications).saveAll(anyIterable<Notification>())
        val from = fakeAccount()
        val transferId = fakeId()
        try {
            kafkaTemplate.send("transfer-completed", transferJson(transferId, from)).get()

            val parked = readUntilFound("transfer-completed-dlt", from)
            assertTrue("\"fromAccountNumber\":\"$from\"" in parked, "DLT에 받은 그대로 옮겨진다: $parked")
            // 처음 1번 + 다시 시도 2번
            verify(notifications, atLeast(3)).saveAll(anyIterable<Notification>())
        } finally {
            reset(notifications)
        }
        assertEquals(0, processedEventCount("transfer-completed:$transferId"), "처리 표시도 롤백돼 남지 않는다")
        assertEquals(emptyList(), notifications.findByAccountNumber(from), "알림도 남지 않는다")
    }

    /** 망가진 JSON은 몇 번을 다시 해도 읽을 수 없어서, 기본 규칙상 다시 시도 없이 DLT로 간다. 이 테스트는 옮겨지는 것과 막히지 않는 것만 본다. */
    @Test
    fun `JSON이 망가진 이벤트는 DLT로 옮기고 그 뒤의 이벤트를 처리한다`() {
        val broken = "망가진-${UUID.randomUUID()}"
        val next = fakeAccount()

        kafkaTemplate.send("subscription-created", broken.toByteArray()).get()
        kafkaTemplate.send("subscription-created", subscriptionJson(next)).get()

        assertEquals(broken, readUntilFound("subscription-created-dlt", broken))
        waitUntil("망가진 이벤트 뒤의 이벤트도 처리") { notifications.findByAccountNumber(next).isNotEmpty() }
    }

    private fun transferJson(transferId: Long, from: String) =
        """{"transferId":$transferId,"fromAccountNumber":"$from","toAccountNumber":"${fakeAccount()}","amount":1000,"completedAt":"2026-11-23T00:00:00Z"}"""
            .toByteArray()

    private fun subscriptionJson(account: String) =
        """{"subscriptionId":${fakeId()},"productId":1,"accountNumber":"$account","subscribedAt":"2026-11-23T00:00:00Z"}"""
            .toByteArray()

    /** 진짜 번호와 겹치지 않게 아주 큰 수를 쓴다. */
    private fun fakeId() = 9_000_000_000L + Random.nextLong(1_000_000_000L)

    private fun fakeAccount() = "9999%08d".format(Random.nextInt(100_000_000))

    /** 토픽을 처음부터 글자로 읽어 text가 들어 있는 메시지를 찾는다. 다시 시도가 끝나기를 기다리므로 최대 30초 본다. */
    private fun readUntilFound(topic: String, text: String): String {
        val properties = Properties().apply {
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        }
        consumerFactory.createConsumer("dlt-test-${UUID.randomUUID()}", null, null, properties).use { consumer ->
            consumer.subscribe(listOf(topic))
            repeat(60) {
                consumer.poll(Duration.ofMillis(500)).map { it.value() }.firstOrNull { text in it }?.let { return it }
            }
        }
        throw AssertionError("$topic 토픽에서 '$text'가 들어 있는 메시지를 찾지 못했다")
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        repeat(150) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("15초 안에 되지 않았다: $what")
    }

    private fun processedEventCount(eventKey: String): Int? =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_key = ?", Int::class.java, eventKey)
}
