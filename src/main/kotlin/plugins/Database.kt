package com.amos_tech_code.plugins

import com.amos_tech_code.data.database.DatabaseFactory
import com.amos_tech_code.data.database.migrateDatabase
import com.amos_tech_code.data.database.seedDatabase
import io.ktor.server.application.*

fun Application.configureDatabase() {
    DatabaseFactory.init()
    seedDatabase()
    migrateDatabase()
}


