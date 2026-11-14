package com.coinbank.transfer

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** 계좌 간 송금. M2에서 락과 멱등 키를 더한다. */
@PackageInfo
@ApplicationModule(displayName = "송금")
class ModuleMetadata
