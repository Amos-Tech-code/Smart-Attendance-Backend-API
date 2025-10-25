package com.amos_tech_code.domain.dtos.requests

import kotlinx.serialization.Serializable

@Serializable
data class StartSessionRequest(
    val universityId: String,
    val programmeIds: List<String>,
    val unitId: String,
    val method: AttendanceMethodRequest,
    val locationLat: Double,
    val locationLng: Double,
    val radiusMeters: Int = 50,
    val durationMinutes: Int = 60
)

@Serializable
enum class AttendanceMethodRequest {
    QR_CODE,       // Students scan QR projected/displayed by lecturer
    MANUAL_CODE,   // Students manually type in a generated session code
}


@Serializable
data class UpdateSessionRequest(
    val programmeIds: List<String>? = null,
    val unitId: String? = null,
    val method: AttendanceMethodRequest? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val radiusMeters: Int? = null,
    val durationMinutes: Int? = null
)


@Serializable
data class EndSessionRequest(
    val sessionId: String
)
