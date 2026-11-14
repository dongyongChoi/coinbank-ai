package com.coinbank.account

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** 계좌 개설·조회, M2부터 원장. 다른 모듈에 의존하지 않는다. */
@PackageInfo
@ApplicationModule(displayName = "계좌", allowedDependencies = [])
class ModuleMetadata
