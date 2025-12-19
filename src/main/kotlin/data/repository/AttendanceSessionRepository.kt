package com.amos_tech_code.data.repository

import com.amos_tech_code.data.database.entities.*
import com.amos_tech_code.data.database.utils.exposedTransaction
import com.amos_tech_code.domain.dtos.response.*
import com.amos_tech_code.domain.models.AttendanceMethod
import com.amos_tech_code.domain.models.AttendanceRecord
import com.amos_tech_code.domain.models.AttendanceSession
import com.amos_tech_code.domain.models.AttendanceSessionStatus
import com.amos_tech_code.domain.models.CreateSessionData
import com.amos_tech_code.domain.models.SessionProgramme
import com.amos_tech_code.domain.models.UpdateSessionData
import com.amos_tech_code.utils.AuthorizationException
import io.ktor.server.plugins.NotFoundException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AttendanceSessionRepository() {

    fun createSession(sessionData: CreateSessionData): UUID = exposedTransaction {
        val sessionId = UUID.randomUUID()

        AttendanceSessionsTable.insert {
            it[id] = sessionId
            it[AttendanceSessionsTable.lecturerId] = sessionData.lecturerId
            it[universityId] = sessionData.universityId
            it[unitId] = sessionData.unitId
            it[sessionCode] = sessionData.sessionCode
            it[allowedMethod] = sessionData.allowedMethod
            it[qrCodeUrl] = sessionData.qrCodeUrl
            it[lecturerLatitude] = sessionData.lecturerLatitude
            it[lecturerLongitude] = sessionData.lecturerLongitude
            it[locationRadius] = sessionData.locationRadius
            it[scheduledStartTime] = sessionData.scheduledStartTime
            it[scheduledEndTime] = sessionData.scheduledEndTime
            it[status] = sessionData.sessionStatus
        }

        sessionId
    }

    fun linkSessionToProgrammes(
        sessionId: UUID,
        programmeIds: List<UUID>,
        unitId: UUID,
        lecturerId: UUID,
        universityId: UUID
    ) = exposedTransaction {
        programmeIds.forEach { programmeId ->
            // Get yearOfStudy directly from LecturerTeachingAssignmentsTable for each programme
            val teachingAssignment = LecturerTeachingAssignmentsTable
                .select(LecturerTeachingAssignmentsTable.yearOfStudy)
                .where {
                    (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                            (LecturerTeachingAssignmentsTable.universityId eq universityId) and
                            (LecturerTeachingAssignmentsTable.unitId eq unitId) and
                            (LecturerTeachingAssignmentsTable.programmeId eq programmeId)
                }
                .singleOrNull()

            val yearOfStudy = teachingAssignment?.get(LecturerTeachingAssignmentsTable.yearOfStudy)
                ?: throw AuthorizationException("No teaching assignment found for programme $programmeId and unit $unitId")

            SessionProgrammesTable.insert {
                it[id] = UUID.randomUUID()
                it[SessionProgrammesTable.sessionId] = sessionId
                it[SessionProgrammesTable.programmeId] = programmeId
                it[SessionProgrammesTable.yearOfStudy] = yearOfStudy
            }
        }
    }

    fun validateLecturerAuthorization(lecturerId: UUID, universityId: UUID, unitId: UUID, programmeIds: List<UUID>) =
        exposedTransaction {
            // Check if lecturer is authorized to teach this unit
            val unauthorizedProgrammes = programmeIds.filterNot { programmeId ->
                LecturerTeachingAssignmentsTable
                    .selectAll()
                    .where {
                        (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                                (LecturerTeachingAssignmentsTable.universityId eq universityId) and
                                (LecturerTeachingAssignmentsTable.unitId eq unitId) and
                                (LecturerTeachingAssignmentsTable.programmeId eq programmeId)
                    }
                    .any()
            }
            if (unauthorizedProgrammes.isNotEmpty()) {
                throw AuthorizationException("Unauthorized for programmes: $unauthorizedProgrammes")
            }

        }

    fun updateSession(
        sessionId: UUID,
        lecturerId: UUID,
        updateData: UpdateSessionData
    ): SessionResponse? = exposedTransaction {
        // Verify session exists and belongs to lecturer
        AttendanceSessionsTable
            .selectAll().where {
                (AttendanceSessionsTable.id eq sessionId) and
                        (AttendanceSessionsTable.lecturerId eq lecturerId) and
                        (AttendanceSessionsTable.status eq AttendanceSessionStatus.ACTIVE)
            }
            .singleOrNull() ?: return@exposedTransaction null

        // Update session table
        AttendanceSessionsTable.update(
            where = {
                (AttendanceSessionsTable.id eq sessionId) and
                        (AttendanceSessionsTable.lecturerId eq lecturerId)
            }
        ) {
            updateData.unitId?.let { unitId ->
                it[AttendanceSessionsTable.unitId] = unitId
            }
            updateData.allowedMethod?.let { method ->
                it[AttendanceSessionsTable.allowedMethod] = method
            }
            updateData.lecturerLatitude?.let { lat ->
                it[AttendanceSessionsTable.lecturerLatitude] = lat
            }
            updateData.lecturerLongitude?.let { lng ->
                it[AttendanceSessionsTable.lecturerLongitude] = lng
            }
            updateData.locationRadius?.let { radius ->
                it[AttendanceSessionsTable.locationRadius] = radius
            }
            updateData.durationMinutes?.let { (duration, newEndTime) ->
                it[AttendanceSessionsTable.durationMinutes] = duration
                it[AttendanceSessionsTable.scheduledEndTime] = newEndTime
            }
            it[AttendanceSessionsTable.updatedAt] = LocalDateTime.now()
        }

        // Update programmes if provided
        updateData.programmeIds?.let { newProgrammeIds ->
            // Get current unitId (either updated or existing)
            val currentUnitId = updateData.unitId ?: AttendanceSessionsTable
                .select(AttendanceSessionsTable.unitId)
                .where { AttendanceSessionsTable.id eq sessionId }
                .single()[AttendanceSessionsTable.unitId]

            // Get universityId
            val universityId = AttendanceSessionsTable
                .select(AttendanceSessionsTable.universityId)
                .where { AttendanceSessionsTable.id eq sessionId }
                .single()[AttendanceSessionsTable.universityId]

            // Delete existing programme links
            SessionProgrammesTable.deleteWhere {
                SessionProgrammesTable.sessionId eq sessionId
            }

            // Add new programme links with yearOfStudy
            newProgrammeIds.forEach { programmeId ->
                val teachingAssignment = LecturerTeachingAssignmentsTable
                    .select(LecturerTeachingAssignmentsTable.yearOfStudy)
                    .where {
                        (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                                (LecturerTeachingAssignmentsTable.universityId eq universityId) and
                                (LecturerTeachingAssignmentsTable.unitId eq currentUnitId) and
                                (LecturerTeachingAssignmentsTable.programmeId eq programmeId)
                    }
                    .singleOrNull()

                val yearOfStudy = teachingAssignment?.get(LecturerTeachingAssignmentsTable.yearOfStudy)
                    ?: throw AuthorizationException("No teaching assignment found for programme $programmeId and unit $currentUnitId")

                SessionProgrammesTable.insert {
                    it[id] = UUID.randomUUID()
                    it[SessionProgrammesTable.sessionId] = sessionId
                    it[SessionProgrammesTable.programmeId] = programmeId
                    it[SessionProgrammesTable.yearOfStudy] = yearOfStudy
                }
            }
        }

        // Return updated session
        getSessionDetails(sessionId)
    }

    fun isSessionCodeUnique(sessionCode: String): Boolean = exposedTransaction {
        AttendanceSessionsTable
            .selectAll().where {
                (AttendanceSessionsTable.sessionCode eq sessionCode) and
                        (AttendanceSessionsTable.status eq AttendanceSessionStatus.ACTIVE)
            }
            .empty()
    }

    fun endSession(lecturerId: UUID, sessionId: UUID): Boolean = exposedTransaction {
        val session = AttendanceSessionsTable
            .selectAll().where {
                (AttendanceSessionsTable.id eq sessionId) and
                        (AttendanceSessionsTable.lecturerId eq lecturerId) and
                        (AttendanceSessionsTable.status eq AttendanceSessionStatus.ACTIVE)
            }
            .singleOrNull()

        session?.let {
            val updatedRows = AttendanceSessionsTable.update(
                where = {
                    (AttendanceSessionsTable.id eq sessionId) and
                            (AttendanceSessionsTable.lecturerId eq lecturerId)
                }
            ) {
                it[status] = AttendanceSessionStatus.ENDED
                it[updatedAt] = LocalDateTime.now()
            }

            updatedRows > 0
        } ?: false
    }

    fun getActiveSession(lecturerId: UUID): SessionResponse? = exposedTransaction {
        val session = AttendanceSessionsTable
            .join(UnitsTable, JoinType.INNER, AttendanceSessionsTable.unitId, UnitsTable.id)
            .selectAll().where {
                (AttendanceSessionsTable.lecturerId eq lecturerId) and
                        (AttendanceSessionsTable.status eq AttendanceSessionStatus.ACTIVE)
            }
            .limit(1)
            .singleOrNull()

        session?.let {
            val programmes = SessionProgrammesTable
                .innerJoin(ProgrammesTable)
                .innerJoin(DepartmentsTable)
                .selectAll().where { SessionProgrammesTable.sessionId eq session[AttendanceSessionsTable.id] }
                .map { row ->
                    ProgrammeInfo(
                        id = row[ProgrammesTable.id].toString(),
                        name = row[ProgrammesTable.name],
                        department = row[DepartmentsTable.name]
                    )
                }

            SessionResponse(
                sessionId = session[AttendanceSessionsTable.id].toString(),
                sessionCode = session[AttendanceSessionsTable.sessionCode],
                qrCodeUrl = session[AttendanceSessionsTable.qrCodeUrl],
                method = session[AttendanceSessionsTable.allowedMethod],
                universityId = session[AttendanceSessionsTable.universityId].toString(),
                programmes = programmes,
                unit = UnitInfo(
                    id = session[UnitsTable.id].toString(),
                    name = session[UnitsTable.name],
                    code = session[UnitsTable.code]
                ),
                location = LocationInfo(
                    latitude = session[AttendanceSessionsTable.lecturerLatitude] ?: 0.0,
                    longitude = session[AttendanceSessionsTable.lecturerLongitude] ?: 0.0,
                    radiusMeters = session[AttendanceSessionsTable.locationRadius]
                ),
                timeInfo = TimeInfo(
                    startTime = session[AttendanceSessionsTable.scheduledStartTime].toString(),
                    endTime = session[AttendanceSessionsTable.scheduledEndTime].toString(),
                    durationMinutes = session[AttendanceSessionsTable.durationMinutes]
                ),
                status = session[AttendanceSessionsTable.status]
            )
        }
    }

    fun findUnitCodeById(unitId: UUID) : String = exposedTransaction {
        UnitsTable
            .select(UnitsTable.code)
            .where { UnitsTable.id eq unitId }
            .map { it[UnitsTable.code] }
            .singleOrNull()
            ?: throw NotFoundException("Unit not found")
    }

    fun getSessionQrCodeUrl(sessionId: UUID): String? = exposedTransaction {
        AttendanceSessionsTable
            .selectAll().where { AttendanceSessionsTable.id eq sessionId }
            .singleOrNull()
            ?.get(AttendanceSessionsTable.qrCodeUrl)
    }

    fun getSessionDetails(sessionId: UUID): SessionResponse? {
        return exposedTransaction {
            val session = AttendanceSessionsTable
                .join(UnitsTable, JoinType.INNER, AttendanceSessionsTable.unitId, UnitsTable.id)
                .selectAll().where { AttendanceSessionsTable.id eq sessionId }
                .singleOrNull()

            // Get linked programmes
            val programmes = SessionProgrammesTable
                .join(ProgrammesTable, JoinType.INNER, SessionProgrammesTable.programmeId, ProgrammesTable.id)
                .join(DepartmentsTable, JoinType.INNER, ProgrammesTable.departmentId, DepartmentsTable.id)
                .selectAll().where { SessionProgrammesTable.sessionId eq sessionId }
                .map { row ->
                    ProgrammeInfo(
                        id = row[ProgrammesTable.id].toString(),
                        name = row[ProgrammesTable.name],
                        department = row[DepartmentsTable.name]
                    )
                }

            session?.let {
                SessionResponse(
                    sessionId = sessionId.toString(),
                    sessionCode = session[AttendanceSessionsTable.sessionCode],
                    qrCodeUrl = session[AttendanceSessionsTable.qrCodeUrl],
                    method = when (session[AttendanceSessionsTable.allowedMethod]) {
                        AttendanceMethod.QR_CODE -> AttendanceMethod.QR_CODE
                        AttendanceMethod.MANUAL_CODE -> AttendanceMethod.MANUAL_CODE
                        else -> AttendanceMethod.QR_CODE
                    },
                    universityId = session[AttendanceSessionsTable.universityId].toString(),
                    programmes = programmes,
                    unit = UnitInfo(
                        id = session[UnitsTable.id].toString(),
                        name = session[UnitsTable.name],
                        code = session[UnitsTable.code]
                    ),
                    location = LocationInfo(
                        latitude = session[AttendanceSessionsTable.lecturerLatitude],
                        longitude = session[AttendanceSessionsTable.lecturerLongitude],
                        radiusMeters = session[AttendanceSessionsTable.locationRadius]
                    ),
                    timeInfo = TimeInfo(
                        startTime = session[AttendanceSessionsTable.scheduledStartTime].toString(),
                        endTime = session[AttendanceSessionsTable.scheduledEndTime].toString(),
                        durationMinutes = session[AttendanceSessionsTable.durationMinutes]
                    ),
                    status = when (session[AttendanceSessionsTable.status]) {
                        AttendanceSessionStatus.ACTIVE -> AttendanceSessionStatus.ACTIVE
                        AttendanceSessionStatus.ENDED -> AttendanceSessionStatus.ENDED
                        AttendanceSessionStatus.CANCELLED -> AttendanceSessionStatus.CANCELLED
                        AttendanceSessionStatus.EXPIRED -> AttendanceSessionStatus.EXPIRED
                        AttendanceSessionStatus.SCHEDULED -> AttendanceSessionStatus.SCHEDULED
                    }
                )
            }
        }
    }

    fun autoExpireSessions() {
        exposedTransaction {
            AttendanceSessionsTable.update({
                (AttendanceSessionsTable.status eq AttendanceSessionStatus.ACTIVE) and
                        (AttendanceSessionsTable.scheduledEndTime lessEq LocalDateTime.now())
            }) {
                it[status] = AttendanceSessionStatus.EXPIRED
            }
        }
    }

    /**
     * Attendance Marking Implementation
     * Link students with universities and programmes on first attendance
     */
    // Add to your AttendanceSessionRepository
    fun hasExistingAttendance(studentId: UUID, sessionId: UUID): Boolean = exposedTransaction {
            AttendanceRecordsTable
                .selectAll().where {
                    (AttendanceRecordsTable.studentId eq studentId) and
                            (AttendanceRecordsTable.sessionId eq sessionId)
                }
                .count() > 0
    }


    fun createAttendanceRecord(
        studentId: UUID,
        sessionId: UUID,
        programmeId: UUID,
        sessionCode: String,
        deviceId: String,
        studentLat: Double?,
        studentLng: Double?,
        isLocationVerified: Boolean,
        isDeviceVerified: Boolean
    ): AttendanceRecord = exposedTransaction {
        val attendanceId = UUID.randomUUID()
        val attendedAt = LocalDateTime.now()

        // Calculate distance if location provided
        val distance = if (studentLat != null && studentLng != null) {
            val session = AttendanceSessionsTable
                .selectAll().where { AttendanceSessionsTable.id eq sessionId }
                .singleOrNull()

            session?.let {
                val lecturerLat = it[AttendanceSessionsTable.lecturerLatitude]
                val lecturerLng = it[AttendanceSessionsTable.lecturerLongitude]

                if (lecturerLat != null && lecturerLng != null) {
                    calculateDistance(
                        lecturerLat,
                        lecturerLng,
                        studentLat,
                        studentLng
                    )
                } else {
                    null
                }
            }
        } else null

        // Get attendance method from session
        val attendanceMethod = AttendanceSessionsTable
            .selectAll().where { AttendanceSessionsTable.id eq sessionId }
            .single()[AttendanceSessionsTable.allowedMethod]

        // Get expected device ID
        val expectedDeviceId = DevicesTable
            .selectAll().where {
                (DevicesTable.studentId eq studentId)
            }
            .orderBy(DevicesTable.lastSeen to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(DevicesTable.deviceId)

        // Check for suspicious activity
        val isSuspicious = !isLocationVerified || !isDeviceVerified
        val suspiciousReason = when {
            !isLocationVerified && !isDeviceVerified -> "Location and device verification failed"
            !isLocationVerified -> "Location verification failed"
            !isDeviceVerified -> "Device verification failed"
            else -> null
        }

        // Insert attendance record
        AttendanceRecordsTable.insert {
            it[id] = attendanceId
            it[AttendanceRecordsTable.sessionId] = sessionId
            it[AttendanceRecordsTable.studentId] = studentId
            it[AttendanceRecordsTable.attendanceMethodUsed] = attendanceMethod
            it[AttendanceRecordsTable.studentLatitude] = studentLat
            it[AttendanceRecordsTable.studentLongitude] = studentLng
            it[AttendanceRecordsTable.distanceFromLecturer] = distance
            it[AttendanceRecordsTable.isLocationVerified] = isLocationVerified
            it[AttendanceRecordsTable.deviceId] = deviceId
            it[AttendanceRecordsTable.expectedDeviceId] = expectedDeviceId
            it[AttendanceRecordsTable.isDeviceVerified] = isDeviceVerified
            it[AttendanceRecordsTable.isSuspicious] = isSuspicious
            it[AttendanceRecordsTable.suspiciousReason] = suspiciousReason
            it[AttendanceRecordsTable.attendedAt] = attendedAt
        }

        // Update device last seen
        //updateDeviceLastSeen(studentId, deviceId)

        AttendanceRecord(
            id = attendanceId,
            attendedAt = attendedAt
        )
    }

    fun getActiveSessionBySessionCodeAndUnitCode(sessionCode: String, unitCode: String): AttendanceSession? =
        exposedTransaction {
            AttendanceSessionsTable
                .join(UnitsTable, JoinType.INNER, AttendanceSessionsTable.unitId, UnitsTable.id)
                .join(LecturersTable, JoinType.INNER, AttendanceSessionsTable.lecturerId, LecturersTable.id)
                .selectAll()
                .where {
                    (AttendanceSessionsTable.sessionCode eq sessionCode) and
                            (UnitsTable.code eq unitCode) and
                            (AttendanceSessionsTable.status eq AttendanceSessionStatus.ACTIVE)
                }
                .orderBy(AttendanceSessionsTable.createdAt to SortOrder.DESC)
                .limit(1)
                .map { row ->
                    AttendanceSession(
                        id = row[AttendanceSessionsTable.id],
                        sessionCode = row[AttendanceSessionsTable.sessionCode],
                        unitId = row[AttendanceSessionsTable.unitId],
                        universityId = row[AttendanceSessionsTable.universityId],
                        lecturerId = row[AttendanceSessionsTable.lecturerId],
                        lecturerLatitude = row[AttendanceSessionsTable.lecturerLatitude],
                        lecturerLongitude = row[AttendanceSessionsTable.lecturerLongitude],
                        locationRadius = row[AttendanceSessionsTable.locationRadius],
                        unitName = row[UnitsTable.name],
                        unitCode = row[UnitsTable.code],
                        lecturerName = row[LecturersTable.fullName] ?: "Unknown"
                    )
                }
                .singleOrNull()
        }

    fun isFirstAttendance(studentId: UUID, sessionId: UUID): Boolean = exposedTransaction {
        AttendanceRecordsTable
            .selectAll().where { AttendanceRecordsTable.studentId eq studentId }
            .count() == 0L
    }

    fun getSessionProgrammes(sessionId: UUID): List<SessionProgramme> = exposedTransaction {
        SessionProgrammesTable
            .join(ProgrammesTable, JoinType.INNER, SessionProgrammesTable.programmeId, ProgrammesTable.id)
            .join(DepartmentsTable, JoinType.INNER, ProgrammesTable.departmentId, DepartmentsTable.id)
            .selectAll().where { SessionProgrammesTable.sessionId eq sessionId }
            .map { row ->
                SessionProgramme(
                    programmeId = row[SessionProgrammesTable.programmeId],
                    programmeName = row[ProgrammesTable.name],
                    departmentName = row[DepartmentsTable.name],
                    yearOfStudy = row[SessionProgrammesTable.yearOfStudy]
                )
            }

    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

}