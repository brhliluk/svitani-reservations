package cz.svitaninymburk.projects.reservations.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabases() {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:sqlite:reservations.db?journal_mode=WAL&busy_timeout=5000"
        driverClassName = "org.sqlite.JDBC"
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_SERIALIZABLE"
    }

    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    // Vytvoření tabulek při startu serveru
    transaction {
        SchemaUtils.create(
            TODO()
        )
    }
}