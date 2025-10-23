package com.amos_tech_code.domain.models

import java.time.LocalDateTime
import java.util.UUID

data class CreateSessionData(
    val lecturerId: UUID,
    val universityId: UUID,
    val unitId: UUID,
    val sessionCode: String,
    val secretKey: String,
    val attendanceMethod: AttendanceMethod,
    val qrCodeUrl: String?,
    val lecturerLatitude: Double,
    val lecturerLongitude: Double,
    val locationRadius: Int,
    val scheduledStartTime: LocalDateTime,
    val actualStartTime: LocalDateTime,
    val scheduledEndTime: LocalDateTime,
    val durationMinutes: Int
)