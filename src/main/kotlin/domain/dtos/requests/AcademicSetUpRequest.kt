package com.amos_tech_code.domain.dtos.requests

import kotlinx.serialization.Serializable

@Serializable
data class AcademicSetupUpRequest(
    val universityName: String,
    val programmes: List<ProgrammeRequest>,
)


@Serializable
data class ProgrammeRequest(
    val name: String,
    val department: String,
    val yearOfStudy: Int,
    val units: List<UnitRequest>,
    val programmeId: String? = null // For existing programmes, null for new ones
)


@Serializable
data class UnitRequest(
    val code: String,
    val name: String,
    val unitId: String? = null // For existing units, null for new ones
)