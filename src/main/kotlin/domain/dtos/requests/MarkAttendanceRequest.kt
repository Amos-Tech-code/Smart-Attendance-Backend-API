package com.amos_tech_code.domain.dtos.requests

import kotlinx.serialization.Serializable

// Enhanced Request DTO
@Serializable
data class MarkAttendanceRequest(
    val sessionCode: String,
    val secretKey: String,
    val deviceId: String,
    val programmeId: String? = null, // Required only for first-time attendance with multiple programmes
    val studentLat: Double? = null,
    val studentLng: Double? = null
)

@Serializable
data class VerifySessionRequest(
    val sessionCode: String,
    val secretKey: String
)
