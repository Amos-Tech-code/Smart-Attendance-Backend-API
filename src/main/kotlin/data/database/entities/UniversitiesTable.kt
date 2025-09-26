package com.amos_tech_code.data.database.entities

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

object UniversitiesTable : Table("universities") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val name: Column<String> = varchar("name", 255)

    val createdAt: Column<LocalDateTime> = datetime("created_at").clientDefault { now() }
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").clientDefault { now() }

    override val primaryKey = PrimaryKey(id)
}