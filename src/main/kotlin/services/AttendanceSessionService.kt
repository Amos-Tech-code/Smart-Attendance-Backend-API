package com.amos_tech_code.services

import com.amos_tech_code.domain.dtos.requests.StartSessionRequest
import com.amos_tech_code.domain.dtos.response.SessionResponse
import java.util.UUID

interface AttendanceSessionService {

    suspend fun startSession(lecturerId: UUID, request: StartSessionRequest): SessionResponse

    suspend fun endSession(lecturerId: UUID, sessionId: String): Boolean

    suspend fun getActiveSession(lecturerId: UUID): SessionResponse

}