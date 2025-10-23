package com.amos_tech_code.data.repository.impl

import com.amos_tech_code.data.database.entities.*
import com.amos_tech_code.data.database.utils.exposedTransaction
import com.amos_tech_code.domain.dtos.requests.AttendanceMethodRequest
import com.amos_tech_code.domain.dtos.requests.StartSessionRequest
import com.amos_tech_code.domain.dtos.response.*
import com.amos_tech_code.domain.models.AttendanceMethod
import com.amos_tech_code.domain.models.AttendanceSessionStatus
import com.amos_tech_code.domain.models.CreateSessionData
import com.amos_tech_code.services.CloudStorageService
import com.amos_tech_code.services.QRCodeService
import com.amos_tech_code.services.SessionCodeGenerator
import com.amos_tech_code.utils.AuthorizationException
import com.amos_tech_code.utils.InternalServerException
import io.ktor.server.plugins.NotFoundException
import org.jetbrains.exposed.sql.*
import java.time.LocalDateTime
import java.util.*

class AttendanceSessionRepository() {

    fun createSession(sessionData: CreateSessionData): UUID = exposedTransaction {
        val sessionId = UUID.randomUUID()

        AttendanceSessionsTable.insert {
            it[id] = sessionId
            it[AttendanceSessionsTable.lecturerId] = sessionData.lecturerId
            it[universityId] = sessionData.universityId
            it[unitId] = sessionData.unitId
            it[sessionCode] = sessionData.sessionCode
            it[secretKey] = sessionData.secretKey
            it[attendanceMethod] = sessionData.attendanceMethod
            it[qrCodeUrl] = sessionData.qrCodeUrl
            it[lecturerLatitude] = sessionData.lecturerLatitude
            it[lecturerLongitude] = sessionData.lecturerLongitude
            it[locationRadius] = sessionData.locationRadius
            it[scheduledStartTime] = sessionData.scheduledStartTime
            it[actualStartTime] = sessionData.actualStartTime
            it[scheduledEndTime] = sessionData.scheduledEndTime
            it[durationMinutes] = sessionData.durationMinutes
            it[status] = AttendanceSessionStatus.ACTIVE
        }

        sessionId
    }

    fun linkSessionToProgrammes(sessionId: UUID, programmeIds: List<UUID>) = exposedTransaction {
        programmeIds.forEach { programmeId ->
            SessionProgrammesTable.insert {
                it[id] = UUID.randomUUID()
                it[SessionProgrammesTable.sessionId] = sessionId
                it[SessionProgrammesTable.programmeId] = programmeId
            }
        }
    }

    fun validateLecturerAuthorization(lecturerId: UUID, universityId: UUID, unitId: UUID, programmeIds: List<UUID>) = exposedTransaction {
        // Check if lecturer is authorized to teach this unit
        val unitAuthorization = LecturerTeachingAssignmentsTable
            .selectAll().where {
                (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                        (LecturerTeachingAssignmentsTable.universityId eq universityId) and
                        (LecturerTeachingAssignmentsTable.unitId eq unitId)
            }
            .singleOrNull()

        if (unitAuthorization == null) {
            throw AuthorizationException("Lecturer is not authorized to teach this unit")
        }

        // Check if all programmes are valid and lecturer is associated with them
        programmeIds.forEach { programmeId ->
            val programmeAuthorization = LecturerTeachingAssignmentsTable
                .selectAll().where {
                    (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                            (LecturerTeachingAssignmentsTable.universityId eq universityId) and
                            (LecturerTeachingAssignmentsTable.programmeId eq programmeId) and
                            (LecturerTeachingAssignmentsTable.unitId eq unitId)
                }
                .singleOrNull()

            if (programmeAuthorization == null) {
                throw AuthorizationException("Lecturer is not authorized to teach unit in programme: $programmeId")
            }
        }
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

        session?.let { row ->
            val updatedRows = AttendanceSessionsTable.update(
                where = {
                    (AttendanceSessionsTable.id eq sessionId) and
                            (AttendanceSessionsTable.lecturerId eq lecturerId)
                }
            ) {
                it[status] = AttendanceSessionStatus.ENDED
                it[actualEndTime] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }

            updatedRows > 0
        } ?: false
    }

    fun getActiveSessions(lecturerId: UUID): SessionResponse? = exposedTransaction {
        val session = AttendanceSessionsTable
            .join(UnitsTable, JoinType.INNER, AttendanceSessionsTable.unitId, UnitsTable.id)
            .selectAll().where {
                (AttendanceSessionsTable.lecturerId eq lecturerId) and
                        (AttendanceSessionsTable.status eq AttendanceSessionStatus.ACTIVE)
            }.singleOrNull()

        // Get linked programmes
        session?.let {
            val programmes = SessionProgrammesTable
                .join(ProgrammesTable, JoinType.INNER, SessionProgrammesTable.programmeId, ProgrammesTable.id)
                .join(DepartmentsTable, JoinType.INNER, ProgrammesTable.departmentId, DepartmentsTable.id)
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
                secretKey = session[AttendanceSessionsTable.secretKey],
                qrCodeUrl = session[AttendanceSessionsTable.qrCodeUrl],
                method = when (session[AttendanceSessionsTable.attendanceMethod]) {
                    AttendanceMethod.QR_CODE -> AttendanceMethod.QR_CODE
                    AttendanceMethod.MANUAL_CODE -> AttendanceMethod.MANUAL_CODE
                    else -> AttendanceMethod.QR_CODE
                },
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
                    startTime = session[AttendanceSessionsTable.actualStartTime].toString(),
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
                    secretKey = session[AttendanceSessionsTable.secretKey],
                    qrCodeUrl = session[AttendanceSessionsTable.qrCodeUrl],
                    method = when (session[AttendanceSessionsTable.attendanceMethod]) {
                        AttendanceMethod.QR_CODE -> AttendanceMethod.QR_CODE
                        AttendanceMethod.MANUAL_CODE -> AttendanceMethod.MANUAL_CODE
                        else -> AttendanceMethod.QR_CODE
                    },
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
                        startTime = session[AttendanceSessionsTable.actualStartTime].toString(),
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

}