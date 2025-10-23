package com.amos_tech_code.services

import com.amos_tech_code.domain.dtos.requests.AcademicSetupUpRequest
import com.amos_tech_code.domain.dtos.response.AcademicSetupResponse
import com.amos_tech_code.domain.dtos.response.LecturerUniversitiesResponse
import java.util.UUID

interface LecturerAcademicService {

    fun saveAcademicSetup(lecturerId: UUID, request: AcademicSetupUpRequest): AcademicSetupResponse

    suspend fun getLecturerAcademicSetup(lecturerId: UUID, universityId: String? = null): LecturerUniversitiesResponse

}