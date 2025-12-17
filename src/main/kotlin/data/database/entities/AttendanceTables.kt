package com.amos_tech_code.data.database.entities

import com.amos_tech_code.domain.models.AttendanceMethod
import com.amos_tech_code.domain.models.AttendanceSessionStatus
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

// Attendance Sessions
object AttendanceSessionsTable : Table("attendance_sessions") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val lecturerId: Column<UUID> = uuid("lecturer_id").references(LecturersTable.id, onDelete = ReferenceOption.CASCADE)
    val universityId: Column<UUID> = uuid("university_id").references(UniversitiesTable.id, onDelete = ReferenceOption.CASCADE)
    val unitId: Column<UUID> = uuid("unit_id").references(UnitsTable.id, onDelete = ReferenceOption.CASCADE)

    // Session Identification
    val sessionCode: Column<String> = varchar("session_code", 6).uniqueIndex() // 6-digit unique code
    val qrCodeUrl: Column<String?> = text("qr_code_url").nullable() // CDN URL for QR code image

    // Session Configuration
    val allowedMethod: Column<AttendanceMethod> = customEnumeration(
        "allowed_method",
        "VARCHAR(20)",
        { value -> AttendanceMethod.valueOf(value as String) },
        { it.name }
    )
    val isLocationRequired: Column<Boolean> = bool("is_location_required").default(false)
    val lecturerLatitude: Column<Double?> = double("lecturer_latitude").nullable()
    val lecturerLongitude: Column<Double?> = double("lecturer_longitude").nullable()
    val locationRadius: Column<Int> = integer("location_radius").default(50) // meters

    // Time Management
    val scheduledStartTime: Column<LocalDateTime?> = datetime("scheduled_start_time").nullable()
    val actualStartTime: Column<LocalDateTime?> = datetime("actual_start_time").nullable()
    val scheduledEndTime: Column<LocalDateTime> = datetime("scheduled_end_time")
    val actualEndTime: Column<LocalDateTime?> = datetime("actual_end_time").nullable()
    val durationMinutes: Column<Int> = integer("duration_minutes").default(60)

    // Security & Limits
    val maxAttempts: Column<Int> = integer("max_attempts").default(5)
    val attemptCount: Column<Int> = integer("attempt_count").default(0)
    val isLocked: Column<Boolean> = bool("is_locked").default(false)

    // Status
    val status: Column<AttendanceSessionStatus> = customEnumeration(
        "status",
        "VARCHAR(20)",
        { value -> AttendanceSessionStatus.valueOf(value as String) },
        { it.name }
    ).default(AttendanceSessionStatus.ACTIVE)

    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, lecturerId, status)
        index(isUnique = false, sessionCode, status)
    }
}

// Session-Programme Mapping (Supports multiple programmes per session)
object SessionProgrammesTable : Table("session_programmes") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val sessionId: Column<UUID> = uuid("session_id").references(AttendanceSessionsTable.id, onDelete = ReferenceOption.CASCADE)
    val programmeId: Column<UUID> = uuid("programme_id").references(ProgrammesTable.id, onDelete = ReferenceOption.CASCADE)
    val yearOfStudy: Column<Int> = integer("year_of_study")
    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_session_programme", sessionId, programmeId)
    }
}

// Attendance Records
object AttendanceRecordsTable : Table("attendance_records") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val sessionId: Column<UUID> = uuid("session_id").references(AttendanceSessionsTable.id, onDelete = ReferenceOption.CASCADE)
    val studentId: Column<UUID> = uuid("student_id").references(StudentsTable.id, onDelete = ReferenceOption.CASCADE)
    val programmeId: Column<UUID> = uuid("programme_id").references(ProgrammesTable.id, onDelete = ReferenceOption.CASCADE)

    // Verification Details
    val attendanceMethodUsed: Column<AttendanceMethod> = customEnumeration(
        "attendance_method_used",
        "VARCHAR(20)",
        { value -> AttendanceMethod.valueOf(value as String) },
        { it.name }
    )
    val sessionCode: Column<String> = varchar("session_code", 6) // Code used for attendance

    // Location Verification
    val studentLatitude: Column<Double?> = double("student_latitude").nullable()
    val studentLongitude: Column<Double?> = double("student_longitude").nullable()
    val distanceFromLecturer: Column<Double?> = double("distance_from_lecturer").nullable() // meters
    val isLocationVerified: Column<Boolean> = bool("is_location_verified").default(false)

    // Device Verification
    val deviceId: Column<String> = varchar("device_id", 255) // Current device used
    val expectedDeviceId: Column<String?> = varchar("expected_device_id", 255).nullable() // Student's registered device
    val isDeviceVerified: Column<Boolean> = bool("is_device_verified").default(false)

    // Flags for monitoring
    val isSuspicious: Column<Boolean> = bool("is_suspicious").default(false)
    val suspiciousReason: Column<String?> = text("suspicious_reason").nullable()

    val attendedAt: Column<LocalDateTime> = datetime("attended_at").clientDefault { now() }
    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_student_session", sessionId, studentId)
        index(isUnique = false, studentId)
        index(isUnique = false, sessionId)
        index(isUnique = false, deviceId)
    }
}
