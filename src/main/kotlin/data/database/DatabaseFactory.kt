package com.amos_tech_code.data.database

import com.amos_tech_code.config.AppConfig
import com.amos_tech_code.data.database.entities.DevicesTable
import com.amos_tech_code.data.database.entities.LecturersTable
import com.amos_tech_code.data.database.entities.StudentsTable
import com.amos_tech_code.data.database.entities.SuspiciousLoginsTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime.now

object DatabaseFactory {

    val config = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl =
            "jdbc:postgresql://${AppConfig.DB_HOST}:${AppConfig.DB_PORT}/${AppConfig.DB_NAME}?sslmode=require"
        username = AppConfig.DB_USER
        password = AppConfig.DB_PASSWORD

        // Connection pool settings
        maximumPoolSize = 20

        // Transactions
        isAutoCommit = false  // Let's data.repository layer manage transactions
    }

    fun init() {
        try {
            val dataSource = HikariDataSource(config)
            Database.connect(dataSource)

            transaction {
                // Create tables if they don't exist
                SchemaUtils.createMissingTablesAndColumns(
                    StudentsTable,
                    DevicesTable,
                    LecturersTable,
                    SuspiciousLoginsTable
                )

                // updateOwnerPassword()
            }
        } catch (e: Exception) {
            println("Database initialization failed: ${e.message}")
            throw e
        }
    }

}


fun Application.migrateDatabase() {
    // This function can be used for future database migrations

}

fun Application.seedDatabase() {
    environment.monitor.subscribe(ApplicationStarted) {
        transaction {
            // Seed admin user if none exist
            // Check if any lecturer exists
            val existingLecturer = LecturersTable
                .selectAll()
                .limit(1)
                .singleOrNull()

            if (existingLecturer == null) {
                println("Seeding default lecturer...")

                LecturersTable.insert {
                    it[email] = "default.lecturer@university.com"
                    it[fullName] = "Default Lecturer"
                    it[isProfileComplete] = false
                    it[isActive] = true
                    it[lastLoginAt] = null
                    it[createdAt] = now()
                    it[updatedAt] = now()
                }

                println("Default lecturer seeded successfully.")
            } else {
                println("Lecturer already exist, skipping seeding.")
            }
        }
    }

}