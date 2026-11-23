package com.coinbank.notification.internal

import org.apache.kafka.common.TopicPartition
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import java.time.Duration

/**
 * 알림 리스너가 이벤트를 처리하지 못했을 때의 규칙. 스프링 부트가 이 빈을 리스너에 붙인다.
 *
 * 1. 쉬는 시간을 1초, 2초, 4초… 로 늘려 가며 maxRetries번 다시 시도한다. DB가 잠깐 멈춘 정도면 이 사이에 풀린다.
 * 2. 그래도 안 되면 이벤트를 "{토픽}-dlt" 토픽(DLT, 못 읽은 이벤트 보관함)으로 옮기고 다음 이벤트로 넘어간다.
 *    옮긴 이벤트는 지워지지 않는다. 원인을 고친 뒤 다시 넣으면 처리 표시(processed_event) 덕분에 두 번 처리되지 않는다.
 *
 * JSON이 망가진 이벤트처럼 몇 번을 다시 해도 안 될 오류는 다시 시도하지 않고 바로 DLT로 옮긴다(DefaultErrorHandler 기본 규칙).
 * 지금은 리스너가 알림 모듈에만 있어서 이 규칙이 앱 전체의 리스너 규칙이 된다.
 */
@Configuration(proxyBeanMethods = false)
class NotificationKafkaConfiguration {

    @Bean
    fun notificationErrorHandler(
        kafkaTemplate: KafkaTemplate<*, *>,
        @Value("\${coinbank.notification.retry.max-retries}") maxRetries: Int,
        @Value("\${coinbank.notification.retry.initial-interval}") initialInterval: Duration,
        @Value("\${coinbank.notification.retry.max-interval}") maxInterval: Duration,
    ): DefaultErrorHandler {
        // 받은 그대로(바이트)를 DLT에 다시 보낸다. 파티션은 -1로 두어 Kafka가 고르게 한다(DLT는 파티션 수가 다를 수 있다).
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            TopicPartition("${record.topic()}-dlt", -1)
        }
        val backOff = ExponentialBackOffWithMaxRetries(maxRetries).apply {
            this.initialInterval = initialInterval.toMillis()
            this.multiplier = 2.0
            this.maxInterval = maxInterval.toMillis()
        }
        return DefaultErrorHandler(recoverer, backOff)
    }
}
