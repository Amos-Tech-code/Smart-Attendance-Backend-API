package com.amos_tech_code.domain.dtos.response

import com.amos_tech_code.domain.models.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class LecturerAuthResponse(
    val token: String,
    val name: String,
    val email: String,
    val profileComplete: Boolean,
    val userType: UserRole,
)