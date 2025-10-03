package com.amos_tech_code

import com.amos_tech_code.plugins.configureAdministration
import com.amos_tech_code.plugins.configureAuthentication
import com.amos_tech_code.plugins.configureDatabase
import com.amos_tech_code.plugins.configureExceptionHandler
import com.amos_tech_code.plugins.configureFrameworks
import com.amos_tech_code.plugins.configureHTTP
import com.amos_tech_code.plugins.configureMonitoring
import com.amos_tech_code.plugins.configureRouting
import com.amos_tech_code.plugins.configureSerialization
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main(args: Array<String>) {

    val env = dotenv()
    val serverPort = env["SERVER_PORT"].toInt()

    embeddedServer(Netty, port = serverPort, module = Application::module)
        .start(wait = true)

}


fun Application.module() {
    configureAdministration()
    configureFrameworks()
    configureSerialization()
    configureDatabase()
    configureMonitoring()
    configureAuthentication()
    configureHTTP()
    configureExceptionHandler()
    configureRouting()
}
