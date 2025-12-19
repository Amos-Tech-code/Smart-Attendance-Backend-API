package com.amos_tech_code.domain.models

import java.util.UUID

data class ResolvedUnit(
    val unitId: UUID,
    val code: String,
    val semester: Int?,
    val isCore: Boolean = true
)