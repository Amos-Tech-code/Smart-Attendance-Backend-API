package com.amos_tech_code.data.database.entities

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime.now

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
