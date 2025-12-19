package com.amos_tech_code.data.database.entities

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime.now

object AcademicTermsTable : Table("academic_terms") {
    val id = uuid("id").autoGenerate()
    val universityId = uuid("university_id")
        .references(UniversitiesTable.id, onDelete = ReferenceOption.CASCADE)
    val academicYear = varchar("academic_year", 9) // "2024-2025"
    val semester = integer("semester") // 1 or 2
    val weekCount = integer("week_count").default(14) // Typical Kenyan semester weeks
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init { uniqueIndex(universityId, academicYear, semester) }
}

