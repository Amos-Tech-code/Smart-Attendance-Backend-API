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


object UniversitiesTable : Table("universities") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val name: Column<String> = varchar("name", 255).uniqueIndex()
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


object DepartmentsTable : Table("departments") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val universityId: Column<UUID> = uuid("university_id")
        .references(UniversitiesTable.id, onDelete = ReferenceOption.CASCADE)
    val name: Column<String> = varchar("name", 255)
    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_department_university", universityId, name)
    }
}


object ProgrammesTable : Table("programmes") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val universityId: Column<UUID> = uuid("university_id")
        .references(UniversitiesTable.id, onDelete = ReferenceOption.CASCADE)
    val departmentId: Column<UUID> = uuid("department_id")
        .references(DepartmentsTable.id, onDelete = ReferenceOption.CASCADE)
    val name: Column<String> = varchar("name", 255)
    val isActive: Column<Boolean> = bool("is_active").default(true)
    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_programme_university", universityId, name)
    }
}


object UnitsTable : Table("units") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val universityId: Column<UUID> = uuid("university_id")
        .references(UniversitiesTable.id, onDelete = ReferenceOption.CASCADE)
    val code: Column<String> = varchar("code", 50)
    val name: Column<String> = varchar("name", 255)
    val isActive: Column<Boolean> = bool("is_active").default(true)
    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_unit_code_university", universityId, code)
    }
}


object ProgrammeUnitsTable : Table("programme_units") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val programmeId: Column<UUID> = uuid("programme_id")
        .references(ProgrammesTable.id, onDelete = ReferenceOption.CASCADE)
    val unitId: Column<UUID> = uuid("unit_id")
        .references(UnitsTable.id, onDelete = ReferenceOption.CASCADE)
    val yearOfStudy: Column<Int> = integer("year_of_study") // Which year this unit is taught in this programme
    val semester: Column<Int?> = integer("semester").nullable() // Optional: which semester
    val isCore: Column<Boolean> = bool("is_core").default(true) // Whether it's a core or elective unit
    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_programme_unit_year", programmeId, unitId, yearOfStudy)
    }
}


object LecturerTeachingAssignmentsTable : Table("lecturer_teaching_assignments") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val lecturerId: Column<UUID> = uuid("lecturer_id")
        .references(LecturersTable.id, onDelete = ReferenceOption.CASCADE)
    val universityId: Column<UUID> = uuid("university_id")
        .references(UniversitiesTable.id, onDelete = ReferenceOption.CASCADE)
    val programmeId: Column<UUID> = uuid("programme_id")
        .references(ProgrammesTable.id, onDelete = ReferenceOption.CASCADE)
    val unitId: Column<UUID> = uuid("unit_id")
        .references(UnitsTable.id, onDelete = ReferenceOption.CASCADE)
    val yearOfStudy: Column<Int> = integer("year_of_study")
    val academicYear: Column<String?> = varchar("academic_year", 9).nullable() // e.g., "2024-2025"
    val semester: Column<Int?> = integer("semester").nullable()
    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_lecturer_assignment", lecturerId, universityId, programmeId, unitId, yearOfStudy, academicYear)
    }
}

