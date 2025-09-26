package com.amos_tech_code.plugins

import com.amos_tech_code.di.appModule
import io.ktor.server.application.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureFrameworks() {

    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

}
