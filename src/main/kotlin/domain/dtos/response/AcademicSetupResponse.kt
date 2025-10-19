package com.amos_tech_code.domain.dtos.response

import kotlinx.serialization.Serializable

@Serializable
data class AcademicSetupResponse(
    val universityName: String,
    val programmes: List<ProgrammeResponse>,
    val createdAt: Long
)


@Serializable
data class ProgrammeResponse(
    val id: String,
    val name: String,
    val department: String,
    val yearOfStudy: Int,
    val units: List<UnitResponse>
)

@Serializable
data class UnitResponse(
    val id: String,
    val code: String,
    val name: String
)