package com.amos_tech_code.domain.models

import java.time.LocalDateTime
import java.util.UUID

data class Student(
    val id: UUID,
    val registrationNumber: String,
    val fullName: String,
    val device: Device?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null,
    val lastLogin: LocalDateTime? = null
)

data class Device(
    val id: UUID,
    val deviceId: String, // Unique device identifier
    val model: String,
    val os: String,
    val fcmToken: String? = null,
    val lastSeen: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null
)