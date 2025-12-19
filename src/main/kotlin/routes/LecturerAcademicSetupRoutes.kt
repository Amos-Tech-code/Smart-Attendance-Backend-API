package com.amos_tech_code.routes

import com.amos_tech_code.domain.dtos.requests.AcademicSetUpRequest
import com.amos_tech_code.domain.models.UserRole
import com.amos_tech_code.services.LecturerAcademicService
import com.amos_tech_code.utils.getUserIdFromJWT
import com.amos_tech_code.utils.getUserRoleFromJWT
import com.amos_tech_code.utils.respondBadRequest
import com.amos_tech_code.utils.respondForbidden
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.lecturerAcademicSetupRoutes(
    lecturerAcademicService : LecturerAcademicService
) {

    route("api/v1/lecturer") {

        post("/academic-setup") {

            val lecturerId = call.getUserIdFromJWT() ?: return@post call.respondBadRequest("Lecturer ID is required")

            if (call.getUserRoleFromJWT()?.uppercase() != UserRole.LECTURER.name) return@post call.respondForbidden()

            val request = call.receive<AcademicSetUpRequest>()

            val academicSetup = lecturerAcademicService.saveAcademicSetup(lecturerId, request)

            call.respond(
                HttpStatusCode.OK,
                academicSetup
            )

        }

        get("/academic-setup") {

            val lecturerId = call.getUserIdFromJWT() ?: return@get call.respondBadRequest("Lecturer ID is required")

            if (call.getUserRoleFromJWT()?.uppercase() != UserRole.LECTURER.name) return@get call.respondForbidden()

            // Optional universityId parameter
            val universityId = call.parameters["universityId"]

            val academicSetup = lecturerAcademicService.getLecturerAcademicSetup(lecturerId, universityId)

            call.respond(
                HttpStatusCode.OK,
                academicSetup
            )
        }

    }
}