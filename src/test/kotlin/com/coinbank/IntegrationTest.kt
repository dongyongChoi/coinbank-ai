package com.coinbank

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import

/**
 * 통합 테스트 공통 설정. 모든 테스트가 같은 설정을 쓰면 스프링 컨텍스트와
 * MySQL 컨테이너가 한 번만 뜨고 재사용된다.
 * 테스트에서는 지표와 트레이스를 내보내지 않는다. 받아 줄 otel-lgtm이 없기 때문이다.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(
    properties = [
        "management.otlp.metrics.export.enabled=false",
        "management.tracing.export.enabled=false",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
annotation class IntegrationTest
