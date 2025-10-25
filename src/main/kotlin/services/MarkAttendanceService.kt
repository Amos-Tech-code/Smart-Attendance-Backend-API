package com.amos_tech_code.services

import com.amos_tech_code.domain.dtos.requests.MarkAttendanceRequest
import com.amos_tech_code.domain.dtos.response.MarkAttendanceResponse
import java.util.UUID

interface MarkAttendanceService {

    suspend fun processIntelligentAttendance(studentId: UUID, request: MarkAttendanceRequest): MarkAttendanceResponse

}
