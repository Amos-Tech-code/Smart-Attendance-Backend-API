package com.amos_tech_code.services.impl

import com.amos_tech_code.data.repository.impl.AttendanceSessionRepository
import com.amos_tech_code.domain.dtos.requests.AttendanceMethodRequest
import com.amos_tech_code.domain.dtos.requests.StartSessionRequest
import com.amos_tech_code.domain.dtos.response.SessionResponse
import com.amos_tech_code.domain.models.AttendanceMethod
import com.amos_tech_code.domain.models.CreateSessionData
import com.amos_tech_code.services.AttendanceSessionService
import com.amos_tech_code.services.CloudStorageService
import com.amos_tech_code.services.QRCodeService
import com.amos_tech_code.services.SessionCodeGenerator
import com.amos_tech_code.utils.AppException
import com.amos_tech_code.utils.InternalServerException
import com.amos_tech_code.utils.ResourceNotFoundException
import com.amos_tech_code.utils.ValidationException
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class AttendanceSessionServiceImpl(
    private val attendanceSessionRepository: AttendanceSessionRepository,
    private val qrCodeService: QRCodeService,
    private val sessionCodeGenerator: SessionCodeGenerator,
    private val cloudStorageService: CloudStorageService
) : AttendanceSessionService {

    override suspend fun startSession(lecturerId: UUID, request: StartSessionRequest): SessionResponse {

        try {
            validateStartSessionRequest(request)

            val universityId = UUID.fromString(request.universityId)
            val unitId = UUID.fromString(request.unitId)
            val programmeIds = request.programmeIds.map { UUID.fromString(it) }

            // Validate lecturer authorization
            attendanceSessionRepository.validateLecturerAuthorization(lecturerId, universityId, unitId, programmeIds)

            // Generate session code and secret
            val sessionCode = generateUniqueSessionCode()
            val secretKey = sessionCodeGenerator.generateSecretKey()

            // Handle QR code generation if needed
            var qrCodeUrl: String? = null
            if (request.method == AttendanceMethodRequest.QR_CODE) {
                qrCodeUrl = generateAndUploadQrCode(sessionCode, secretKey)
            }

            val scheduledStartTime = LocalDateTime.now()
            val scheduledEndTime = scheduledStartTime.plusMinutes(request.durationMinutes.toLong())

            // Create session data
            val sessionData = CreateSessionData(
                lecturerId = lecturerId,
                universityId = universityId,
                unitId = unitId,
                sessionCode = sessionCode,
                secretKey = secretKey,
                attendanceMethod = when (request.method) {
                    AttendanceMethodRequest.QR_CODE -> AttendanceMethod.QR_CODE
                    AttendanceMethodRequest.MANUAL_CODE -> AttendanceMethod.MANUAL_CODE
                },
                qrCodeUrl = qrCodeUrl,
                lecturerLatitude = request.locationLat,
                lecturerLongitude = request.locationLng,
                locationRadius = request.radiusMeters,
                scheduledStartTime = scheduledStartTime,
                actualStartTime = LocalDateTime.now(),
                scheduledEndTime = scheduledEndTime,
                durationMinutes = request.durationMinutes
            )

            var sessionId : UUID? = null
            // Create session and link programmes
            transaction {
                sessionId = attendanceSessionRepository.createSession(sessionData)
                attendanceSessionRepository.linkSessionToProgrammes(sessionId, programmeIds)
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
        return attendanceSessionRepository.getActiveSessions(lecturerId)
            ?: throw ResourceNotFoundException("No active session found")
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

    private suspend fun generateAndUploadQrCode(sessionCode: String, secretKey: String): String {
        return try {
            val sessionId = UUID.randomUUID() // Temporary ID for QR data
            val qrCodeData = qrCodeService.generateQRCodeData(sessionCode, secretKey, sessionId)
            val qrCodeImage = qrCodeService.generateQRCodeImage(qrCodeData, 400, 400)
            val fileName = "qr_${sessionId}_${System.currentTimeMillis()}.png"
            cloudStorageService.uploadQRCode(qrCodeImage, fileName)
        } catch (ex: Exception) {
            throw InternalServerException("Failed to generate and upload QR code: ${ex.message}")
        }
    }

    private fun validateStartSessionRequest(request: StartSessionRequest) {
        if (request.universityId.isBlank()) {
            throw ValidationException("University ID is required")
        }
        if (request.programmeIds.isEmpty()) {
            throw ValidationException("At least one programme ID is required")
        }
        if (request.unitId.isBlank()) {
            throw ValidationException("Unit ID is required")
        }
        if (request.locationLat < -90 || request.locationLat > 90) {
            throw ValidationException("Invalid latitude value")
        }
        if (request.locationLng < -180 || request.locationLng > 180) {
            throw ValidationException("Invalid longitude value")
        }
        if (request.radiusMeters !in 1..1000) {
            throw ValidationException("Location radius must be between 1 and 1000 meters")
        }
        if (request.durationMinutes !in 1..240) {
            throw ValidationException("Duration must be between 1 and 240 minutes")
        }

        // Validate UUID formats
        try {
            UUID.fromString(request.universityId)
            UUID.fromString(request.unitId)
            request.programmeIds.forEach { UUID.fromString(it) }
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Invalid ID format")
        }
    }
}