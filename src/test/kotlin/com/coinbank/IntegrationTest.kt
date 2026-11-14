package com.coinbank

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import

/**
 * 통합 테스트 공통 설정. 모든 테스트가 같은 설정을 쓰면 스프링 컨텍스트와
 * MySQL 컨테이너가 한 번만 뜨고 재사용된다.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
annotation class IntegrationTest
