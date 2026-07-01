package com.github.yumelira.yumebox.runtime.service.di

import com.github.yumelira.yumebox.data.gateway.LogRecordGateway
import com.github.yumelira.yumebox.data.gateway.RuntimeLogWriter
import com.github.yumelira.yumebox.runtime.service.LogRecordService
import com.github.yumelira.yumebox.runtime.service.LogRecordServiceGateway
import org.koin.dsl.module

val runtimeServiceModule = module {
    single<LogRecordGateway> { LogRecordServiceGateway() }
    single<RuntimeLogWriter> { RuntimeLogWriter { line -> LogRecordService.writeLog(line) } }
}
