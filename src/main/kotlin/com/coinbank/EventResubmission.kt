package com.coinbank

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 못 보낸 이벤트를 다시 보낸다. event_publication에서 완료 표시(completion_date)가 없는 줄이 못 보낸 이벤트다.
 *
 * 두 경우를 함께 다룬다.
 * - Kafka가 꺼져 있거나 대답이 없어 보내기가 실패한 이벤트
 * - 커밋은 됐는데 보내기 전에 앱이 죽어서 한 번도 나가지 않은 이벤트
 *
 * 너무 일찍 다시 보내면 아직 보내는 중인 이벤트를 한 번 더 보낸다. 그래서 olderThan은
 * Kafka를 기다리는 최대 시간(spring.kafka.producer.properties.delivery.timeout.ms)보다 길어야 한다.
 * 그래도 두 번 갈 수는 있다. 받는 쪽이 같은 이벤트를 한 번만 처리하게 한다(M3-7).
 */
@Component
class EventResubmission(
    private val incompletePublications: IncompleteEventPublications,
    @Value("\${coinbank.events.resubmit-older-than}") private val olderThan: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${coinbank.events.resubmit-interval}")
    fun resubmit() {
        log.debug("{}보다 오래된 못 보낸 이벤트를 다시 보낸다", olderThan)
        incompletePublications.resubmitIncompletePublicationsOlderThan(olderThan)
    }
}

/** @Scheduled가 동작하게 한다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration
