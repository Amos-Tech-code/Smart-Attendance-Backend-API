package com.amos_tech_code.domain.dtos.response

import com.amos_tech_code.domain.models.AttendanceMethod
import com.amos_tech_code.domain.models.AttendanceSessionStatus
import com.amos_tech_code.domain.models.FlagType
import com.amos_tech_code.domain.models.SeverityLevel
import kotlinx.serialization.Serializable

@Serializable
data class SessionResponse(
    val sessionId: String,
    val sessionCode: String, // 6-digit code
    val secretKey: String, // 8-char secret
    val qrCodeUrl: String?, // CDN URL if QR method
    val method: AttendanceMethod,
    val programmes: List<ProgrammeInfo>,
    val unit: UnitInfo,
    val location: LocationInfo,
    val timeInfo: TimeInfo,
    val status: AttendanceSessionStatus
)

@Serializable
data class ProgrammeInfo(
    val id: String,
    val name: String,
    val department: String
)

@Serializable
data class UnitInfo(
    val id: String,
    val name: String,
    val code: String
)

@Serializable
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int
)

@Serializable
data class TimeInfo(
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int
)

@Serializable
data class AttendanceResponse(
    val success: Boolean,
    val sessionId: String,
    val studentId: String,
    val verification: VerificationResult,
    val flags: List<AttendanceFlag>,
    val attendedAt: String
)

@Serializable
data class VerificationResult(
    val locationVerified: Boolean,
    val deviceVerified: Boolean,
    val methodVerified: Boolean,
    val overallVerified: Boolean
)

@Serializable
data class AttendanceFlag(
    val type: FlagType,
    val message: String,
    val severity: SeverityLevel
)
