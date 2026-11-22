package com.coinbank.notification

import com.coinbank.IntegrationTest
import com.coinbank.KafkaTest
import com.coinbank.account.AccountService
import com.coinbank.notification.internal.NotificationRepository
import com.coinbank.transfer.TransferService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals

/** Kafka의 이벤트가 알림이 되는지, 같은 이벤트를 두 번 받아도 알림이 한 번만 생기는지 확인한다. */
@IntegrationTest
@KafkaTest
class NotificationTests(
    @Autowired private val transferService: TransferService,
    @Autowired private val accountService: AccountService,
    @Autowired private val notifications: NotificationRepository,
    @Autowired private val kafkaTemplate: KafkaTemplate<String, ByteArray>,
) {

    @Test
    fun `송금하면 보낸 사람과 받는 사람에게 알림이 한 건씩 생긴다`() {
        val from = accountService.open("김코인", 10_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        transferService.transfer(from, to, 3_000, UUID.randomUUID().toString())

        waitUntil("두 계좌에 알림이 생긴다") { messagesOf(from).isNotEmpty() && messagesOf(to).isNotEmpty() }
        assertEquals(listOf("3,000원 출금 (받는 계좌 $to)"), messagesOf(from))
        assertEquals(listOf("3,000원 입금 (보낸 계좌 $from)"), messagesOf(to))
    }

    /**
     * 다시 보내기(M3-6)로 같은 이벤트가 두 번 오는 상황을 만든다. 같은 JSON을 토픽에 직접 두 번 넣는다.
     * "두 번째를 처리했는지"를 알려고 뒤에 표시용 이벤트를 하나 더 넣는다. 토픽의 파티션이 하나라 순서대로 읽으므로,
     * 표시용 알림이 생겼으면 그 앞의 두 번째 이벤트도 이미 처리된 것이다.
     */
    @Test
    fun `같은 이벤트를 두 번 받아도 알림은 한 번만 생긴다`() {
        val duplicate = transferJson(transferId = fakeTransferId(), from = fakeAccount(), to = fakeAccount())
        val marker = transferJson(transferId = fakeTransferId(), from = fakeAccount(), to = fakeAccount())

        kafkaTemplate.send("transfer-completed", duplicate.json).get()
        kafkaTemplate.send("transfer-completed", duplicate.json).get()
        kafkaTemplate.send("transfer-completed", marker.json).get()

        waitUntil("표시용 이벤트까지 처리") { messagesOf(marker.to).isNotEmpty() }
        assertEquals(1, messagesOf(duplicate.from).size, "보낸 계좌의 알림 수")
        assertEquals(1, messagesOf(duplicate.to).size, "받은 계좌의 알림 수")
    }

    private data class TransferJson(val from: String, val to: String, val json: ByteArray)

    /** TransferCompleted를 Modulith가 보내는 것과 같은 모양의 JSON으로 만든다. */
    private fun transferJson(transferId: Long, from: String, to: String) = TransferJson(
        from,
        to,
        """{"transferId":$transferId,"fromAccountNumber":"$from","toAccountNumber":"$to","amount":1000,"completedAt":"2026-11-22T00:00:00Z"}"""
            .toByteArray(),
    )

    /** 진짜 송금 번호와 겹치지 않게 아주 큰 수를 쓴다. */
    private fun fakeTransferId() = 9_000_000_000L + Random.nextLong(1_000_000_000L)

    private fun fakeAccount() = "9999%08d".format(Random.nextInt(100_000_000))

    private fun messagesOf(accountNumber: String): List<String> =
        notifications.findByAccountNumber(accountNumber).map { it.message }

    /** Kafka를 거쳐 알림이 생기기까지 시간이 걸린다. 최대 15초 기다린다. */
    private fun waitUntil(what: String, condition: () -> Boolean) {
        repeat(150) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("15초 안에 되지 않았다: $what")
    }
}
