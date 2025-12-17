package com.amos_tech_code.utils

import java.time.LocalDateTime

fun String.toLocalDateTimeOrThrow(): LocalDateTime =
    try {
        LocalDateTime.parse(this)
    } catch (e: Exception) {
        throw ValidationException("Scheduled start time must be a valid ISO-8601 datetime")
    }
