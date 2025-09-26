package com.amos_tech_code.data.repository

import com.amos_tech_code.domain.models.Lecturer
import java.time.LocalDateTime
import java.util.UUID

interface LecturerRepository {

    suspend fun findByEmail(email: String): Lecturer?

    suspend fun findById(id: UUID): Lecturer?

    suspend fun create(lecturer: Lecturer): Lecturer

    suspend fun updateProfileComplete(lecturerId: UUID, complete: Boolean): Boolean

    suspend fun updateLastLogin(lecturerId: UUID, timestamp: LocalDateTime): Boolean

}