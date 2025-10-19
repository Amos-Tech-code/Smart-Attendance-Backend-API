package com.amos_tech_code.routes

import com.amos_tech_code.domain.dtos.requests.AcademicSetupUpRequest
import com.amos_tech_code.domain.dtos.response.AcademicSetupResponse
import com.amos_tech_code.domain.models.UserRole
import com.amos_tech_code.services.impl.LecturerAcademicServiceImpl
import com.amos_tech_code.utils.ValidationException
import com.amos_tech_code.utils.getUserIdFromJWT
import com.amos_tech_code.utils.getUserRoleFromJWT
import com.amos_tech_code.utils.respondBadRequest
import com.amos_tech_code.utils.respondForbidden
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.lecturerAcademicSetupRoutes(
    lecturerAcademicService : LecturerAcademicServiceImpl
) {

    route("api/v1/lecturer") {

        post("/academic-setup") {

            val lecturerId = call.getUserIdFromJWT()
                ?: return@post call.respondBadRequest("Lecturer ID is required")

            val role = call.getUserRoleFromJWT() ?: return@post call.respondForbidden()
            if (role.uppercase() != UserRole.LECTURER.name) return@post call.respondForbidden()

            val request = call.receive<AcademicSetupUpRequest>()

            val academicSetup = lecturerAcademicService.saveAcademicSetup(lecturerId, request)

            call.respond(
                HttpStatusCode.OK,
                academicSetup
            )

        }

    }
}