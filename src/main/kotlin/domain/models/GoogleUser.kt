package com.amos_tech_code.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class GoogleUser(
    val email: String,
    val name: String? = null,
    val emailVerified: Boolean
)