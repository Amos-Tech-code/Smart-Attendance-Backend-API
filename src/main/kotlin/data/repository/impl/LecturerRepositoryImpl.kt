package com.amos_tech_code.data.repository.impl

import com.amos_tech_code.data.database.entities.LecturersTable
import com.amos_tech_code.data.repository.LecturerRepository
import com.amos_tech_code.domain.models.Lecturer
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

class LecturerRepositoryImpl : LecturerRepository {

    override suspend fun findByEmail(email: String): Lecturer? {
        return transaction {
            LecturersTable
                .selectAll().where { LecturersTable.email eq email }
                .map { it.toLecturer() }
                .singleOrNull()
        }
    }

    override suspend fun findById(id: UUID): Lecturer? {
        return transaction {
            LecturersTable
                .selectAll().where { LecturersTable.id eq id }
                .map { it.toLecturer() }
                .singleOrNull()
        }
    }

    override suspend fun create(lecturer: Lecturer): Lecturer {
        return transaction {
            LecturersTable.insert {
                it[id] = lecturer.id
                it[email] = lecturer.email
                it[fullName] = lecturer.name
                it[isProfileComplete] = lecturer.isProfileComplete
                it[createdAt] = lecturer.createdAt
                it[updatedAt] = lecturer.updatedAt
            }.resultedValues?.single()?.toLecturer() ?: throw Exception("Failed to create lecturer")
        }
    }

    override suspend fun updateProfileComplete(lecturerId: UUID, complete: Boolean): Boolean {
        return transaction {
            LecturersTable.update({ LecturersTable.id eq lecturerId }) {
                it[isProfileComplete] = complete
                it[updatedAt] = LocalDateTime.now()
            } > 0
        }
    }

    override suspend fun updateLastLogin(
        lecturerId: UUID,
        timestamp: LocalDateTime
    ): Boolean {
        return transaction {
            LecturersTable.update({ LecturersTable.id eq lecturerId }) {
                it[lastLoginAt] = timestamp
                it[updatedAt] = LocalDateTime.now()
            } > 0
        }
    }


    fun ResultRow.toLecturer(): Lecturer {
        return Lecturer(
            id = this[LecturersTable.id],
            email = this[LecturersTable.email],
            name = this[LecturersTable.fullName],
            isProfileComplete = this[LecturersTable.isProfileComplete],
            lastLoginAt = this[LecturersTable.lastLoginAt],
            createdAt = this[LecturersTable.createdAt],
            updatedAt = this[LecturersTable.updatedAt]
        )
    }

}