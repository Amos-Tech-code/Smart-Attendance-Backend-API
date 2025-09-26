package com.amos_tech_code.data.repository

import com.amos_tech_code.domain.dtos.requests.DeviceInfo
import com.amos_tech_code.domain.models.Device
import com.amos_tech_code.domain.models.Student
import java.time.LocalDateTime
import java.util.UUID

interface StudentRepository {

    suspend fun findByRegistrationNumber(regNo: String): Student?

    suspend fun findById(id: UUID): Student?

    suspend fun findByDeviceId(deviceId: String): Student?

    suspend fun findDeviceByStudentId(studentId: UUID): Device?

    suspend fun createStudentWithDevice(student: Student): Student

    suspend fun updateDevice(studentId: UUID, device: Device): Boolean

    suspend fun updateLastLogin(studentId: UUID, timestamp: LocalDateTime): Boolean

    suspend fun updateDeviceLastSeen(deviceId: String, timestamp: LocalDateTime): Boolean

    suspend fun flagSuspiciousLogin(studentId: UUID, device: DeviceInfo): Boolean

}