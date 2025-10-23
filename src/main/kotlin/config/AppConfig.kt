package com.amos_tech_code.config

import io.github.cdimascio.dotenv.dotenv
import kotlin.text.toInt
import kotlin.text.toLong

object AppConfig {

    private val env = dotenv {
        ignoreIfMissing = true
        filename = ".env"
    }

    // Server Configuration
    val SERVER_HOST = env["SERVER_HOST"]
    val SERVER_PORT = env["SERVER_PORT"]?.toInt()

    // JWT Configuration
    val JWT_SECRET = env["JWT_SECRET"]
    val JWT_ISSUER = env["JWT_ISSUER"]
    val JWT_AUDIENCE = env["JWT_AUDIENCE"]
    val JWT_REALM = env["JWT_REALM"]
    val JWT_EXPIRATION = env["JWT_EXPIRATION"]?.toLong()

    // Database Configuration
    val DB_HOST = env["DB_HOST"]
    val DB_PORT = env["DB_PORT"]?.toInt()
    val DB_NAME = env["DB_NAME"]
    val DB_USER = env["DB_USER"]
    val DB_PASSWORD = env["DB_PASSWORD"]

    // Cloudinary Configuration
    val CLOUD_NAME = env["CLOUDINARY_NAME"] ?: throw IllegalArgumentException("CLOUDINARY_NAME is required")
    val API_KEY = env["CLOUDINARY_API_KEY"] ?: throw IllegalArgumentException("CLOUDINARY_API_KEY is required")
    val API_SECRET = env["CLOUDINARY_API_SECRET"] ?: throw IllegalArgumentException("CLOUDINARY_API_SECRET is required")

}