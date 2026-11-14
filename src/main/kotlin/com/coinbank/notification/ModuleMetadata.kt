package com.coinbank.notification

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** 송금·개설 이벤트 알림. M3에서 구현하고 M5에서 별도 서비스로 분리한다. */
@PackageInfo
@ApplicationModule(displayName = "알림")
class ModuleMetadata
