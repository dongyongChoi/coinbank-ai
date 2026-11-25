package com.coinbank

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import

/**
 * 통합 테스트 공통 설정. 모든 테스트가 같은 설정을 쓰면 스프링 컨텍스트와
 * MySQL 컨테이너가 한 번만 뜨고 재사용된다.
 * 테스트에서는 지표와 트레이스를 내보내지 않는다. 받아 줄 otel-lgtm이 없기 때문이다.
 * 이벤트를 Kafka로 보내는 것(externalization)도 끈다. Kafka 컨테이너가 없는 테스트에서 켜 두면, 보내기를 맡은 스레드가
 * 없는 Kafka를 기다리다가 컨텍스트를 닫을 때 테스트 클래스마다 30초쯤 늦어진다. 이벤트 기록(event_publication)은 그대로 남는다.
 * Kafka에서 읽는 리스너도 띄우지 않는다. 켜 두면 없는 Kafka에 계속 붙으려 한다.
 * Kafka가 필요한 테스트는 @KafkaTest를 함께 붙여 둘 다 다시 켠다.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(
    properties = [
        "management.otlp.metrics.export.enabled=false",
        "management.tracing.export.enabled=false",
        "spring.modulith.events.externalization.enabled=false",
        "spring.kafka.listener.auto-startup=false",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
annotation class IntegrationTest
