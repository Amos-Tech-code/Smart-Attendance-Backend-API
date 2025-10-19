package com.amos_tech_code.utils

import com.amos_tech_code.domain.dtos.response.GenericResponseDto
import com.amos_tech_code.domain.models.UserRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import java.util.UUID

suspend fun ApplicationCall.respondUnauthorized() {
    respond(
        HttpStatusCode.Unauthorized,
        GenericResponseDto(HttpStatusCode.Unauthorized.value, message = "Unauthorized")
    )
}

suspend fun ApplicationCall.respondBadRequest(message: String) {
    respond(
        HttpStatusCode.BadRequest,
        GenericResponseDto(HttpStatusCode.BadRequest.value, message = message)
    )
}

suspend fun ApplicationCall.respondForbidden(message: String? = null) {
    respond(
        HttpStatusCode.Forbidden,
        GenericResponseDto(HttpStatusCode.Forbidden.value, message = message ?: "You do not have permission to access this resource.")
    )
}

fun ApplicationCall.getUserIdFromJWT() : UUID? {
    return principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()?.let { UUID.fromString(it) }
}

fun ApplicationCall.getUserRoleFromJWT() : String? {
    return principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
}