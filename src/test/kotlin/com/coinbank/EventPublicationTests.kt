package com.coinbank

import com.coinbank.account.AccountService
import com.coinbank.account.InsufficientBalanceException
import com.coinbank.product.ProductService
import com.coinbank.transfer.TransferService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.ConsumerFactory
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Outbox 확인. 송금·가입이 커밋되면 이벤트가 event_publication 테이블에 남고 Kafka 토픽에 도착한다.
 * 롤백되면 이벤트는 적히지도 보내지지도 않는다.
 * 이벤트는 계좌 번호로 찾는다. 계좌 번호는 무작위라 다른 테스트의 이벤트와 섞이지 않는다.
 */
@IntegrationTest
@KafkaTest
@Import(RedisTestcontainersConfiguration::class)
class EventPublicationTests(
    @Autowired private val transferService: TransferService,
    @Autowired private val productService: ProductService,
    @Autowired private val accountService: AccountService,
    @Autowired private val consumerFactory: ConsumerFactory<String, String>,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `송금이 끝나면 이벤트가 기록되고 Kafka에 도착한다`() {
        val from = accountService.open("김코인", 10_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        transferService.transfer(from, to, 3_000, UUID.randomUUID().toString())

        val message = readUntilFound("transfer-completed", from)
        assertTrue("\"toAccountNumber\":\"$to\"" in message && "\"amount\":3000" in message, "도착한 이벤트: $message")
        assertEquals(1, publicationCount(from), "event_publication에 남은 기록 수")
        waitUntil("Kafka로 보낸 뒤 완료 표시") { incompleteCount(from) == 0 }
    }

    @Test
    fun `송금이 실패하면 이벤트는 기록되지 않는다`() {
        val from = accountService.open("김코인", 1_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        assertFailsWith<InsufficientBalanceException> {
            transferService.transfer(from, to, 5_000, UUID.randomUUID().toString())
        }

        assertEquals(0, publicationCount(from), "롤백된 송금의 이벤트 기록 수")
    }

    @Test
    fun `적금에 가입하면 이벤트가 기록되고 Kafka에 도착한다`() {
        val product = productService.create("특판 적금", quota = 1)
        val account = accountService.open("김코인").accountNumber

        productService.subscribe(product.productId, account)

        val message = readUntilFound("subscription-created", account)
        assertTrue("\"productId\":${product.productId}" in message, "도착한 이벤트: $message")
        assertEquals(1, publicationCount(account), "event_publication에 남은 기록 수")
    }

    /**
     * 토픽을 처음부터 읽어 text가 들어 있는 메시지를 찾는다. 커밋 뒤에 보내므로 조금 늦게 올 수 있어 최대 10초 기다린다.
     * Modulith는 이벤트를 JSON 바이트로 보내고 읽는 쪽 기본값도 바이트로 바꾼다. 여기서는 내용을 보려고 글자로 읽는다.
     */
    private fun readUntilFound(topic: String, text: String): String {
        val properties = Properties().apply {
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        }
        consumerFactory.createConsumer("event-test-${UUID.randomUUID()}", null, null, properties).use { consumer ->
            consumer.subscribe(listOf(topic))
            repeat(20) {
                consumer.poll(Duration.ofMillis(500)).map { it.value() }.firstOrNull { text in it }?.let { return it }
            }
        }
        throw AssertionError("$topic 토픽에서 '$text'가 들어 있는 메시지를 찾지 못했다")
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        repeat(50) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("5초 안에 되지 않았다: $what")
    }

    private fun publicationCount(text: String): Int? =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ?",
            Int::class.java,
            "%$text%",
        )

    private fun incompleteCount(text: String): Int? =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NULL",
            Int::class.java,
            "%$text%",
        )
}
