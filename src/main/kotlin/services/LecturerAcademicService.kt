package com.amos_tech_code.services

import com.amos_tech_code.domain.dtos.requests.AcademicSetUpRequest
import com.amos_tech_code.domain.dtos.response.AcademicSetupResponse
import com.amos_tech_code.domain.dtos.response.LecturerUniversitiesResponse
import java.util.UUID

interface LecturerAcademicService {

    suspend fun saveAcademicSetup(lecturerId: UUID, request: AcademicSetUpRequest): AcademicSetupResponse

    suspend fun getLecturerAcademicSetup(lecturerId: UUID, universityId: String? = null): LecturerUniversitiesResponse

}