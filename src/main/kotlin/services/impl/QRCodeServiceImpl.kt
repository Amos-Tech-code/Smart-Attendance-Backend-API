package com.amos_tech_code.services.impl

import com.amos_tech_code.services.QRCodeService
import com.amos_tech_code.utils.InternalServerException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.*

class QRCodeServiceImpl : QRCodeService {

    private val logger = LoggerFactory.getLogger(QRCodeServiceImpl::class.java)

    private val qrCodeWriter = QRCodeWriter()
    private val jsonMapper = ObjectMapper().registerKotlinModule()

    // Optimized for mobile scanning
    private companion object {
        const val MOBILE_WIDTH = 350
        const val MOBILE_HEIGHT = 350
        const val MAX_DATA_LENGTH = 4000
    }

    override fun generateQRCodeImage(data: String, width: Int, height: Int): ByteArray {
        validateParameters(data, width, height)

        return try {
            logger.debug("Generating QR code for mobile: ${width}x${height}, dataLength=${data.length}")

            val hints = mutableMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q) // Quarter error correction - good for mobile
                put(EncodeHintType.MARGIN, 1) // Minimal margin for better mobile scanning
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }

            val bitMatrix: BitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hints)

            ByteArrayOutputStream().use { outputStream ->
                MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
                outputStream.toByteArray().also {
                    logger.debug("QR code generated successfully: ${it.size} bytes")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to generate QR code image: ${e.message}")
            throw InternalServerException("Failed to generate QR code: ${e.message}")
        }
    }

    override fun generateQRCodeData(
        sessionCode: String,
        secretKey: String,
        sessionId: UUID
    ): String {
        validateSessionData(sessionCode, secretKey, sessionId)

        return try {
            val qrData = QRCodeData(
                sessionCode = sessionCode,
                secretKey = secretKey,
                sessionId = sessionId.toString(),
                timestamp = System.currentTimeMillis(),
                version = "1.0"
            )

            val jsonData = jsonMapper.writeValueAsString(qrData)

            if (jsonData.length > MAX_DATA_LENGTH) {
                logger.warn("QR code data length (${jsonData.length}) exceeds recommended limit")
            }

            logger.debug("Generated QR code data for session: $sessionCode")
            jsonData
        } catch (e: Exception) {
            logger.error("Failed to generate QR code data: ${e.message}")
            throw InternalServerException("Failed to generate QR code data: ${e.message}")
        }
    }

    /**
     * Generate QR code optimized specifically for mobile scanning
     */
    fun generateMobileQRCode(data: String): ByteArray {
        return generateQRCodeImage(data, MOBILE_WIDTH, MOBILE_HEIGHT)
    }

    /**
     * Validate QR code data structure
     */
    fun validateQRCodeData(qrDataJson: String): Boolean {
        return try {
            val qrData = jsonMapper.readValue(qrDataJson, QRCodeData::class.java)
            validateSessionData(qrData.sessionCode, qrData.secretKey, UUID.fromString(qrData.sessionId))
            true
        } catch (e: Exception) {
            logger.warn("Invalid QR code data: ${e.message}")
            false
        }
    }

    /**
     * Parse QR code data from JSON string
     */
    fun parseQRCodeData(qrDataJson: String): QRCodeData? {
        return try {
            jsonMapper.readValue(qrDataJson, QRCodeData::class.java)
        } catch (e: Exception) {
            logger.warn("Failed to parse QR code data: ${e.message}")
            null
        }
    }

    private fun validateParameters(data: String, width: Int, height: Int) {
        if (data.isBlank()) {
            throw IllegalArgumentException("QR code data cannot be blank")
        }
        if (data.length > MAX_DATA_LENGTH) {
            throw IllegalArgumentException("QR code data too long: ${data.length} > $MAX_DATA_LENGTH")
        }
        if (width < 100 || width > 2000) {
            throw IllegalArgumentException("Width must be between 100 and 2000")
        }
        if (height < 100 || height > 2000) {
            throw IllegalArgumentException("Height must be between 100 and 2000")
        }
    }

    private fun validateSessionData(sessionCode: String, secretKey: String, sessionId: UUID) {
        if (sessionCode.isBlank()) {
            throw IllegalArgumentException("Session code cannot be blank")
        }
        if (sessionCode.length != 6) {
            throw IllegalArgumentException("Session code must be 6 digits")
        }
        if (!sessionCode.matches(Regex("\\d{6}"))) {
            throw IllegalArgumentException("Session code must contain only digits")
        }
        if (secretKey.isBlank()) {
            throw IllegalArgumentException("Secret key cannot be blank")
        }
        if (secretKey.length != 8) {
            throw IllegalArgumentException("Secret key must be 8 characters")
        }
    }
}

/**
 * Data class for QR code content
 */
data class QRCodeData(
    val sessionCode: String,
    val secretKey: String,
    val sessionId: String,
    val timestamp: Long,
    val version: String
)