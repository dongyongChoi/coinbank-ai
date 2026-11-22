package com.coinbank.notification

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * 송금·가입 알림. Kafka에서 이벤트를 읽어 알림을 남긴다. M5에서 별도 서비스로 분리한다.
 * 다른 모듈의 코드를 쓰지 않는다(allowedDependencies 비움). 이벤트 모양도 이 모듈 안에 따로 둔다.
 * 그래야 M5에서 이 폴더를 그대로 떼어 낼 수 있다.
 */
@PackageInfo
@ApplicationModule(displayName = "알림", allowedDependencies = [])
class ModuleMetadata
