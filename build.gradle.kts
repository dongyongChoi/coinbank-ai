plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.coinbank"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // 관측: 지표·트레이스·로그를 OTLP로 내보낸다 (받는 쪽은 docker-compose의 otel-lgtm).
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    // Outbox: 이벤트를 업무 데이터와 같은 트랜잭션에서 event_publication 테이블에 적는다(이벤트 발행 기록).
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    // @Externalized가 붙은 이벤트를 커밋 뒤에 Kafka로 보낸다.
    implementation("org.springframework.modulith:spring-modulith-events-kafka")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Redis 분산 락. 스타터가 Spring Data Redis 설정(spring.data.redis.*)으로 RedissonClient를 만들어 준다.
    implementation("org.redisson:redisson-spring-boot-starter:4.8.0")
    // Kafka로 이벤트를 보내고 받는다 (M3). 접속 정보는 spring.kafka.*에 있다.
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// JPA 엔티티는 지연 로딩 프록시를 만들 수 있게 열어 둔다 (Kotlin 클래스는 기본이 final)
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
