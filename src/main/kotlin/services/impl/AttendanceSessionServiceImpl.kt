package com.amos_tech_code.services.impl

import com.amos_tech_code.data.repository.AttendanceSessionRepository
import com.amos_tech_code.data.repository.ProgrammeRepository
import com.amos_tech_code.domain.dtos.requests.AttendanceMethodRequest
import com.amos_tech_code.domain.dtos.requests.StartSessionRequest
import com.amos_tech_code.domain.dtos.requests.UpdateSessionRequest
import com.amos_tech_code.domain.dtos.requests.VerifySessionRequest
import com.amos_tech_code.domain.dtos.response.ProgrammeInfoResponse
import com.amos_tech_code.domain.dtos.response.SessionInfo
import com.amos_tech_code.domain.dtos.response.SessionResponse
import com.amos_tech_code.domain.dtos.response.VerifyAttendanceResponse
import com.amos_tech_code.domain.models.AttendanceMethod
import com.amos_tech_code.domain.models.AttendanceSessionStatus
import com.amos_tech_code.domain.models.CreateSessionData
import com.amos_tech_code.domain.models.UpdateSessionData
import com.amos_tech_code.services.AttendanceSessionService
import com.amos_tech_code.services.CloudStorageService
import com.amos_tech_code.services.QRCodeService
import com.amos_tech_code.services.SessionCodeGenerator
import com.amos_tech_code.utils.AppException
import com.amos_tech_code.utils.ConflictException
import com.amos_tech_code.utils.InternalServerException
import com.amos_tech_code.utils.ResourceNotFoundException
import com.amos_tech_code.utils.ValidationException
import com.amos_tech_code.utils.toLocalDateTimeOrThrow
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class AttendanceSessionServiceImpl(
    private val attendanceSessionRepository: AttendanceSessionRepository,
    private val programmeRepository: ProgrammeRepository,
    private val qrCodeService: QRCodeService,
    private val sessionCodeGenerator: SessionCodeGenerator,
    private val cloudStorageService: CloudStorageService
) : AttendanceSessionService {

    override suspend fun startSession(lecturerId: UUID, request: StartSessionRequest): SessionResponse {

        try {
            if (attendanceSessionRepository.getActiveSession(lecturerId) != null) {
                throw ConflictException("You already have an active session.")
            }

            validateStartSessionRequest(request)

            val universityId = UUID.fromString(request.universityId)
            val unitId = UUID.fromString(request.unitId)
            val programmeIds = request.programmeIds.map { UUID.fromString(it) }

            // Validate lecturer authorization
            attendanceSessionRepository.validateLecturerAuthorization(lecturerId, universityId, unitId, programmeIds)

            // Generate unique session code
            val sessionCode = generateUniqueSessionCode()
            val unitCode = attendanceSessionRepository.findUnitCodeById(unitId)

            // Handle QR code generation
            val qrCodeUrl = generateAndUploadQrCode(sessionCode, unitCode)

            val scheduledStartTime = request.scheduledStartTime?.toLocalDateTimeOrThrow() ?: LocalDateTime.now()
            val scheduledEndTime = scheduledStartTime.plusMinutes(request.durationMinutes.toLong())

            // Create session data
            val sessionData = CreateSessionData(
                lecturerId = lecturerId,
                universityId = universityId,
                unitId = unitId,
                sessionCode = sessionCode,
                allowedMethod = when (request.allowedMethod) {
                    AttendanceMethodRequest.QR_CODE -> AttendanceMethod.QR_CODE
                    AttendanceMethodRequest.MANUAL_CODE -> AttendanceMethod.MANUAL_CODE
                    AttendanceMethodRequest.ANY -> AttendanceMethod.ANY
                },
                qrCodeUrl = qrCodeUrl,
                isLocationRequired = request.isLocationRequired,
                lecturerLatitude = request.location?.latitude,
                lecturerLongitude = request.location?.longitude,
                locationRadius = request.radiusMeters,
                scheduledStartTime = scheduledStartTime,
                scheduledEndTime = scheduledEndTime,
                durationMinutes = request.durationMinutes,
                sessionStatus = if (scheduledStartTime == null) AttendanceSessionStatus.ACTIVE else AttendanceSessionStatus.SCHEDULED
            )

            var sessionId : UUID? = null
            // Create session and link programmes
            transaction {
                sessionId = attendanceSessionRepository.createSession(sessionData)
                attendanceSessionRepository.linkSessionToProgrammes(
                    sessionId = sessionId,
                    programmeIds = programmeIds,
                    unitId = unitId,
                    lecturerId = lecturerId,
                    universityId = universityId
                )
            }

            // Return the created session
            if (sessionId != null) {
                return attendanceSessionRepository.getSessionDetails(sessionId) ?: throw InternalServerException("Failed to retrieve created session")
            } else {
                throw IllegalStateException("Failed to start attendance session")
            }

        } catch (ex: Exception) {
            when (ex) {
                is AppException -> throw ex
                else -> throw InternalServerException("Failed to start attendance session: ${ex.message}")
            }
        }
    }

    override suspend fun updateSession(
        lecturerId: UUID,
        sessionId: String,
        request: UpdateSessionRequest
    ): SessionResponse {

        return try {
            validateUpdateSessionRequest(request)

            val sessionUUID = UUID.fromString(sessionId)
            // Get existing session to verify ownership
            val existingSession = attendanceSessionRepository.getSessionDetails(sessionUUID)
                ?: throw ResourceNotFoundException("Session not found")

            // Verify lecturer owns this session
            if (existingSession.sessionId != lecturerId.toString()) {
                throw ResourceNotFoundException("Session not found")
            }

            // Validate authorization if unit is being changed
            request.unitId?.let { newUnitId ->
                val universityId = UUID.fromString(existingSession.universityId)
                val programmeIds = request.programmeIds?.map { UUID.fromString(it) }
                    ?: existingSession.programmes.map { UUID.fromString(it.id) }

                attendanceSessionRepository.validateLecturerAuthorization(
                    lecturerId,
                    universityId,
                    UUID.fromString(newUnitId),
                    programmeIds
                )
            }

            // Update session in repository
            val updatedSession = attendanceSessionRepository.updateSession(
                sessionUUID,
                lecturerId,
                UpdateSessionData(
                    programmeIds = request.programmeIds?.map { UUID.fromString(it) },
                    unitId = request.unitId?.let { UUID.fromString(it) },
                    allowedMethod = request.allowedMethod?.let { when (it) {
                        AttendanceMethodRequest.QR_CODE -> AttendanceMethod.QR_CODE
                        AttendanceMethodRequest.MANUAL_CODE -> AttendanceMethod.MANUAL_CODE
                        AttendanceMethodRequest.ANY -> AttendanceMethod.ANY
                    }},
                    isLocationRequired = request.isLocationRequired,
                    lecturerLatitude = request.location?.latitude,
                    lecturerLongitude = request.location?.longitude,
                    locationRadius = request.radiusMeters,
                    durationMinutes = request.durationMinutes?.let {
                        // Adjust end time if duration changes
                        val newEndTime = LocalDateTime.now().plusMinutes(it.toLong())
                        it to newEndTime
                    }
                )
            )

            updatedSession ?: throw InternalServerException("Failed to update session")

        } catch (ex: Exception) {
            when (ex) {
                is AppException -> throw ex
                else -> throw InternalServerException("Failed to update session: ${ex.message}")
            }
        }
    }

    override suspend fun endSession(lecturerId: UUID, sessionId: String): Boolean {
        if (sessionId.isBlank()) {
            throw ValidationException("Session ID is required")
        }

        val sessionUUID = UUID.fromString(sessionId)

        // Get QR code URL before ending session to delete from cloud storage
        val qrCodeUrl = attendanceSessionRepository.getSessionQrCodeUrl(sessionUUID)

        // End the session
        val success = attendanceSessionRepository.endSession(lecturerId, sessionUUID)

        // Delete QR code from cloud storage if session was ended successfully
        if (success && qrCodeUrl != null) {
            cloudStorageService.deleteQRCode(qrCodeUrl)
        }

        return success
    }

    override suspend fun getActiveSession(lecturerId: UUID): SessionResponse {
        return attendanceSessionRepository.getActiveSession(lecturerId)
            ?: throw ResourceNotFoundException("No active session found")
    }

    override suspend fun verifySessionForAttendance(studentId: UUID, request: VerifySessionRequest): VerifyAttendanceResponse {
        try {// Validate request
            validateVerifySessionRequest(request)

            // Get active session
            val session = attendanceSessionRepository.getActiveSessionBySessionCodeAndUnitCode(
                request.sessionCode,
                request.unitCode
            ) ?: throw ResourceNotFoundException("Invalid session or session has ended")

            // Check if first attendance
            val isFirstAttendance = attendanceSessionRepository.isFirstAttendance(studentId, session.id)

            if (!isFirstAttendance) {
                // Not first time - no programme selection needed
                return VerifyAttendanceResponse(
                    requiresProgrammeSelection = false,
                    availableProgrammes = emptyList(),
                    sessionInfo = SessionInfo(
                        sessionId = session.id.toString(),
                        unitName = session.unitName,
                        unitCode = session.unitCode,
                        lecturerName = session.lecturerName
                    )
                )
            }

            // First time attendance - check if programme selection is needed
            val sessionProgrammes = attendanceSessionRepository.getSessionProgrammes(session.id)

            if (sessionProgrammes.isEmpty()) {
                throw ValidationException("No programmes associated with this session")
            }

            return VerifyAttendanceResponse(
                requiresProgrammeSelection = sessionProgrammes.size > 1,
                availableProgrammes = sessionProgrammes.map { programme ->
                    ProgrammeInfoResponse(
                        id = programme.programmeId.toString(),
                        name = programme.programmeName,
                        department = programme.departmentName,
                        yearOfStudy = programme.yearOfStudy
                    )
                },
                sessionInfo = SessionInfo(
                    sessionId = session.id.toString(),
                    unitName = session.unitName,
                    unitCode = session.unitCode,
                    lecturerName = session.lecturerName
                )
            )
        } catch (ex: Exception) {
            when (ex) {
                is AppException -> throw ex
                else -> throw InternalServerException("Attendance verification failed: ${ex.message}")
            }
        }
    }

    private fun generateUniqueSessionCode(): String {
        var attempts = 0
        val maxAttempts = 10

        while (attempts < maxAttempts) {
            val code = sessionCodeGenerator.generateSixDigitCode()
            val isUnique = attendanceSessionRepository.isSessionCodeUnique(code)

            if (isUnique) {
                return code
            }
            attempts++
        }

        throw InternalServerException("Failed to generate unique 6-digit session code after $maxAttempts attempts")
    }

    private suspend fun generateAndUploadQrCode(sessionCode: String, unitCode: String): String {
        return try {
            val sessionId = UUID.randomUUID() // Temporary ID for QR data
            val qrCodeData = qrCodeService.generateQRCodeData(sessionCode, unitCode, sessionId)
            val qrCodeImage = qrCodeService.generateQRCodeImage(qrCodeData, 300, 300)
            val fileName = "qr_${sessionId}_${System.currentTimeMillis()}.png"
            cloudStorageService.uploadQRCode(qrCodeImage, fileName)
        } catch (ex: Exception) {
            throw InternalServerException("Failed to generate and upload QR code: ${ex.message}")
        }
    }

    private fun validateStartSessionRequest(request: StartSessionRequest) {

        // --- Required IDs ---
        if (request.universityId.isBlank()) {
            throw ValidationException("University ID is required")
        }

        if (request.programmeIds.isEmpty()) {
            throw ValidationException("At least one programme ID is required")
        }

        if (request.programmeIds.size > 10) {
            throw ValidationException("Maximum number of programmes allowed is 10")
        }

        if (request.unitId.isBlank()) {
            throw ValidationException("Unit ID is required")
        }

        // --- UUID format validation ---
        try {
            UUID.fromString(request.universityId)
            UUID.fromString(request.unitId)
            request.programmeIds.forEach { UUID.fromString(it) }
        } catch (e: IllegalArgumentException) {
            throw ValidationException("One or more IDs are not valid UUIDs")
        }

        // --- Location validation (conditional) ---
        if (request.isLocationRequired) {
            val location = request.location
                ?: throw ValidationException("Location is required when location tracking is enabled")

            if (location.latitude !in -90.0..90.0) {
                throw ValidationException("Invalid latitude value")
            }

            if (location.longitude !in -180.0..180.0) {
                throw ValidationException("Invalid longitude value")
            }

            if (request.radiusMeters !in 1..1000) {
                throw ValidationException("Location radius must be between 1 and 1000 meters")
            }
        }

        // --- Session duration ---
        if (request.durationMinutes !in 1..240) {
            throw ValidationException("Duration must be between 1 and 240 minutes")
        }

        // --- Scheduled start time ---
        request.scheduledStartTime?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                throw ValidationException("Scheduled start time must be a valid")
            }
        }
    }

    private fun validateUpdateSessionRequest(request: UpdateSessionRequest) {
        if (request.isLocationRequired == true) {
            val location = request.location
                ?: throw ValidationException("Location is required when location tracking is enabled")

            if (location.latitude !in -90.0..90.0) {
                throw ValidationException("Invalid latitude value")
            }

            if (location.longitude !in -180.0..180.0) {
                throw ValidationException("Invalid longitude value")
            }

            if (request.radiusMeters !in 1..1000) {
                throw ValidationException("Location radius must be between 1 and 1000 meters")
            }
        }

        request.radiusMeters?.let { radius ->
            if (radius !in 1..1000) throw ValidationException("Location radius must be between 1 and 1000 meters")
        }

        request.durationMinutes?.let { duration ->
            if (duration !in 1..240) throw ValidationException("Duration must be between 1 and 240 minutes")
        }

        // Validate UUID formats if provided
        request.unitId?.let {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                throw ValidationException("Invalid unit ID format")
            }
        }

        request.programmeIds?.size?.let {
            if (it > 10) {
                throw ValidationException("Maximum number of Programmes allowed is 10")
            }
        }

        request.programmeIds?.forEach { programmeId ->
            try {
                UUID.fromString(programmeId)
            } catch (e: IllegalArgumentException) {
                throw ValidationException("Invalid programme ID format")
            }
        }

        // --- Scheduled start time ---
        request.scheduledStartTime?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                throw ValidationException("Scheduled start time must be a valid")
            }
        }

    }

    private fun validateVerifySessionRequest(request: VerifySessionRequest) {
        if (request.sessionCode.isBlank()) {
            throw ValidationException("Invalid QR code")
        }
        if (request.sessionCode.length != 6) {
            throw ValidationException("Invalid QR code")
        }
        if (!request.sessionCode.matches(Regex("\\d{6}"))) {
            throw ValidationException("Invalid QR code")
        }
        if (request.unitCode.isBlank()) {
            throw ValidationException("Invalid QR code")
        }
        if (request.unitCode.length != 8) {
            throw ValidationException("Invalid QR code")
        }
    }


}