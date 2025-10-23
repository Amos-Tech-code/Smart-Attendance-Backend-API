package com.amos_tech_code.routes

import com.amos_tech_code.domain.dtos.requests.EndSessionRequest
import com.amos_tech_code.domain.dtos.requests.StartSessionRequest
import com.amos_tech_code.domain.dtos.response.GenericResponseDto
import com.amos_tech_code.domain.models.UserRole
import com.amos_tech_code.services.AttendanceSessionService
import com.amos_tech_code.utils.getUserIdFromJWT
import com.amos_tech_code.utils.getUserRoleFromJWT
import com.amos_tech_code.utils.respondBadRequest
import com.amos_tech_code.utils.respondForbidden
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.attendanceSessionRoutes(
    attendanceSessionService: AttendanceSessionService
) {

    route("api/v1/attendance") {

        post("/sessions/start") {
            val lecturerId = call.getUserIdFromJWT() ?: return@post call.respondBadRequest("Lecturer ID is required")
            if (call.getUserRoleFromJWT()?.uppercase() != UserRole.LECTURER.name) return@post call.respondForbidden()

            val request = call.receive<StartSessionRequest>()

            val session = attendanceSessionService.startSession(lecturerId, request)

            call.respond(
                HttpStatusCode.Created,
                session,
            )

        }

        post("/sessions/end") {
            val lecturerId = call.getUserIdFromJWT() ?: return@post call.respondBadRequest("Lecturer ID is required")
            if (call.getUserRoleFromJWT()?.uppercase() != UserRole.LECTURER.name) return@post call.respondForbidden()

            val request = call.receive<EndSessionRequest>()

            val success = attendanceSessionService.endSession(lecturerId, request.sessionId)

            if (success) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound,
                    GenericResponseDto(
                    statusCode = HttpStatusCode.NotFound.value,
                    message = "Session not found",
                ))
            }


        }

        get("/sessions/active") {
            val lecturerId = call.getUserIdFromJWT() ?: return@get call.respondBadRequest("Lecturer ID is required")
            if (call.getUserRoleFromJWT()?.uppercase() != UserRole.LECTURER.name) return@get call.respondForbidden()

            val activeSession = attendanceSessionService.getActiveSession(lecturerId)

            call.respond(HttpStatusCode.NoContent,
                activeSession
            )

        }
    }
}