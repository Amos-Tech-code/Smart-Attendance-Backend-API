package com.amos_tech_code.utils

import io.ktor.http.HttpStatusCode

sealed class AppException(
    message: String,
    httpStatus: HttpStatusCode,
) : Exception(message)

class ValidationException(
    message: String = "Invalid Request",
) : AppException(message, HttpStatusCode.BadRequest)

class AuthenticationException(
    message: String = "Authentication failed",
) : AppException(message, HttpStatusCode.Unauthorized)

class AuthorizationException(
    message: String = "You don't have permission to access this resource.",
) : AppException(message, HttpStatusCode.Forbidden)

class ResourceNotFoundException(
    message: String = "The requested resource was not found.",
) : AppException(message, HttpStatusCode.NotFound)

class ConflictException(
    message: String = "Resource conflict",
) : AppException(message, HttpStatusCode.Conflict)

class InternalServerException(
    message: String = "Internal server error",
) : AppException(message, HttpStatusCode.InternalServerError)