package com.amos_tech_code.data.database.utils

import org.jetbrains.exposed.sql.transactions.transaction

// Helper function for repository methods that don't need their own transaction boundary
fun <T> exposedTransaction(statement: () -> T): T {
    return transaction {
        statement()
    }
}