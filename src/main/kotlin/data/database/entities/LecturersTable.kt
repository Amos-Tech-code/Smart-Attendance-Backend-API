package com.amos_tech_code.data.database.entities

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

object LecturersTable : Table("lecturers") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val email: Column<String> = varchar("email", 255).uniqueIndex()
    val fullName: Column<String?> = varchar("full_name", 255).nullable()
    val isProfileComplete: Column<Boolean> = bool("is_profile_complete").default(false)

    val lastLoginAt: Column<LocalDateTime?> = datetime("last_login_at").nullable()
    val isActive: Column<Boolean> = bool("isActive").default(true)

    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)
}

object LecturerUniversitiesTable : Table("lecturer_universities") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val lecturerId: Column<UUID> = uuid("lecturer_id")
        .references(LecturersTable.id, onDelete = ReferenceOption.CASCADE)
    val universityId: Column<UUID> = uuid("university_id")
        .references(UniversitiesTable.id, onDelete = ReferenceOption.CASCADE)

    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_lecturer_university", lecturerId, universityId)
    }
}


