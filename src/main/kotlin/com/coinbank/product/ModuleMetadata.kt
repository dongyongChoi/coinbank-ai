package com.coinbank.product

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** 선착순 특판 적금. 가입할 계좌가 있는지 확인하려고 account 모듈만 쓴다. */
@PackageInfo
@ApplicationModule(displayName = "특판 적금", allowedDependencies = ["account"])
class ModuleMetadata
