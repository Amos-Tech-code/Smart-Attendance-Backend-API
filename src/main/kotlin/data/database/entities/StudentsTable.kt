package com.amos_tech_code.data.database.entities

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

object StudentsTable : Table("students") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val registrationNumber: Column<String> = varchar("reg_no", 255)
    val fullName: Column<String> = varchar("full_name", 255)

    val lastLoginAt: Column<LocalDateTime?> = datetime("last_login_at").nullable()
    val isActive: Column<Boolean> = bool("is_active").default(true)

    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex("unique_reg_no", registrationNumber)
    }
}

object DevicesTable : Table("student_devices") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val studentId: Column<UUID> = uuid("student_id").references(StudentsTable.id, onDelete = ReferenceOption.CASCADE)

    val deviceId: Column<String> = varchar("device_id", 255)
    val deviceModel: Column<String> = varchar("model", 100)
    val os: Column<String> = varchar("os", 50)
    val fcmToken: Column<String?> = varchar("fcm_token", 255).nullable()

    val lastSeen: Column<LocalDateTime> = datetime("last_seen").clientDefault { now() }
    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_student_device", studentId, deviceId)
    }

}


object SuspiciousLoginsTable : Table("suspicious_logins") {
    val id = uuid("id").autoGenerate()
    val studentId = uuid("student_id").references(StudentsTable.id, onDelete = ReferenceOption.CASCADE)
    val attemptedDeviceId = varchar("attempted_device_id", 255)
    val attemptedModel = varchar("attempted_model", 255)
    val attemptedOs = varchar("attempted_os", 255)
    val attemptedFcmToken = varchar("attempted_fcm_token", 255).nullable()
    val createdAt = datetime("created_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)
}

