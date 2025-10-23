package com.amos_tech_code.services

import java.util.UUID

interface QRCodeService {

    fun generateQRCodeImage(data: String, width: Int, height: Int): ByteArray

    fun generateQRCodeData(sessionCode: String, secretKey: String, sessionId: UUID): String

}