package com.amos_tech_code.plugins

import com.amos_tech_code.domain.dtos.response.GenericResponseDto
import com.amos_tech_code.routes.authRoutes
import com.amos_tech_code.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting() {

    val authService by inject<AuthService>()

    routing {

        get("/") {
            call.respond(
                HttpStatusCode.OK,
                GenericResponseDto(
                    HttpStatusCode.OK.value,
                    "✅ SmartAttend API is running"
                )
            )
        }

        authRoutes(authService)
    }
}
