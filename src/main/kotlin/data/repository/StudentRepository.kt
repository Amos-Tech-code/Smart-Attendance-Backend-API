package com.amos_tech_code.data.repository

import com.amos_tech_code.data.database.entities.DevicesTable
import com.amos_tech_code.data.database.entities.StudentsTable
import com.amos_tech_code.data.database.entities.SuspiciousLoginsTable
import com.amos_tech_code.domain.dtos.requests.DeviceInfo
import com.amos_tech_code.domain.models.Device
import com.amos_tech_code.domain.models.Student
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class StudentRepository() {

    fun findByRegistrationNumber(regNo: String): Student? = transaction {
        StudentsTable
            .selectAll().where { StudentsTable.registrationNumber eq regNo }
            .map { it.toStudent() }
            .singleOrNull()
    }

    fun findById(id: UUID): Student? = transaction {
        StudentsTable
            .selectAll().where { StudentsTable.id eq id }
            .map { it.toStudent() }
            .singleOrNull()
    }

    fun findByDeviceId(deviceId: String): Student? {
        return transaction {
            (StudentsTable innerJoin DevicesTable)
                .selectAll().where { DevicesTable.deviceId eq deviceId }
                .map { it.toStudentWithDevice() }
                .singleOrNull()
        }
    }

    fun findDeviceByStudentId(studentId: UUID): Device? = transaction {
        DevicesTable
            .selectAll()
            .where { DevicesTable.studentId eq studentId }
            .map {
                Device(
                    id = it[DevicesTable.id],
                    deviceId = it[DevicesTable.deviceId],
                    model = it[DevicesTable.deviceModel],
                    os = it[DevicesTable.os],
                    lastSeen = it[DevicesTable.lastSeen],
                    createdAt = it[DevicesTable.createdAt],
                    updatedAt = it[DevicesTable.updatedAt]
                )
            }
            .singleOrNull()
    }


    fun createStudentWithDevice(
        student: Student,
    ): Student = transaction {
        // Insert student
        StudentsTable.insert {
            it[id] = student.id
            it[registrationNumber] = student.registrationNumber
            it[fullName] = student.fullName
            it[createdAt] = LocalDateTime.now()
            it[updatedAt] = LocalDateTime.now()
        }

        // Insert device linked to student
        student.device?.let {
            DevicesTable.insert {
                it[id] = student.device.id
                it[DevicesTable.studentId] = student.id
                it[deviceId] = student.device.deviceId
                it[deviceModel] = student.device.model
                it[os] = student.device.os
                it[fcmToken] = student.device.fcmToken
                it[lastSeen] = LocalDateTime.now()
                it[createdAt] = LocalDateTime.now()
            }
        }

        // Return student if all succeeded
        student
    }


    fun updateDevice(studentId: UUID, device: Device): Boolean = transaction {
        DevicesTable.insert {
            it[id] = device.id
            it[DevicesTable.studentId] = studentId
            it[deviceId] = device.deviceId
            it[deviceModel] = device.model
            it[os] = device.os
            it[fcmToken] = device.fcmToken
            it[lastSeen] = device.lastSeen
            it[createdAt] = device.createdAt
        }
        true
    }

    fun updateLastLogin(studentId: UUID, timestamp: LocalDateTime): Boolean = transaction {
        StudentsTable.update({ StudentsTable.id eq studentId }) {
            it[lastLoginAt] = timestamp
            it[updatedAt] = timestamp
        } > 0
    }

    fun updateDeviceLastSeen(
        deviceId: String,
        timestamp: LocalDateTime
    ): Boolean = transaction {
        DevicesTable.update({ DevicesTable.deviceId eq deviceId }) {
            it[lastSeen] = timestamp
        } > 0
    }

    fun flagSuspiciousLogin(studentId: UUID, device: DeviceInfo): Boolean = transaction {
        SuspiciousLoginsTable.insert {
            it[id] = UUID.randomUUID()
            it[SuspiciousLoginsTable.studentId] = studentId
            it[attemptedDeviceId] = device.deviceId
            it[attemptedModel] = device.model
            it[attemptedOs] = device.os
            it[attemptedFcmToken] = device.fcmToken
            it[createdAt] = LocalDateTime.now()
        }
        true
    }

    // helpers
    private fun ResultRow.toStudent(): Student = Student(
        id = this[StudentsTable.id],
        registrationNumber = this[StudentsTable.registrationNumber],
        fullName = this[StudentsTable.fullName],
        createdAt = this[StudentsTable.createdAt],
        device = null,
    )

    private fun ResultRow.toStudentWithDevice(): Student = Student(
        id = this[StudentsTable.id],
        registrationNumber = this[StudentsTable.registrationNumber],
        fullName = this[StudentsTable.fullName],
        createdAt = this[StudentsTable.createdAt],
        updatedAt = this[StudentsTable.updatedAt],
        lastLogin = this[StudentsTable.lastLoginAt],
        device = Device(
            id = this[DevicesTable.id],
            deviceId = this[DevicesTable.deviceId],
            model = this[DevicesTable.deviceModel],
            os = this[DevicesTable.os],
            fcmToken = this[DevicesTable.fcmToken],
            lastSeen = this[DevicesTable.lastSeen],
            createdAt = this[DevicesTable.createdAt],
            updatedAt = this[DevicesTable.updatedAt]
        )
    )
}
