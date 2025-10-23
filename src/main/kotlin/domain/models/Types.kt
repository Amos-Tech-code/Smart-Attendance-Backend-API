package com.amos_tech_code.domain.models


enum class UserRole {
    STUDENT, LECTURER, ADMIN // Admin for future impl
}


enum class AttendanceMethod {
    QR_CODE, // Qr Code scan
    MANUAL_CODE, // Manually entering session code if allowed
    LECTURER_MANUAL // Lecturer manually signing for student
}

enum class AttendanceSessionStatus {
    SCHEDULED, ACTIVE, ENDED, CANCELLED, EXPIRED
}


enum class FlagType {
    LOCATION_MISMATCH, DEVICE_MISMATCH, MULTIPLE_ATTEMPTS, SUSPICIOUS_DEVICE
}

enum class SeverityLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

