package com.amos_tech_code.domain.models

import java.time.LocalDateTime
import java.util.UUID


data class UpdateSessionData(
    val programmeIds: List<UUID>? = null,
    val unitId: UUID? = null,
    val allowedMethod: AttendanceMethod? = null,
    val isLocationRequired: Boolean? = null,
    val lecturerLatitude: Double? = null,
    val lecturerLongitude: Double? = null,
    val locationRadius: Int? = null,
    val durationMinutes: Pair<Int, LocalDateTime>? = null
)