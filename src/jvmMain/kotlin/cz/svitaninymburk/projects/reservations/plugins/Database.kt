package cz.svitaninymburk.projects.reservations.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import cz.svitaninymburk.projects.reservations.repository.auth.RefreshTokensTable
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventInstancesTable
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesTable
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
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

    transaction {
        SchemaUtils.create(
            UsersTable,
            RefreshTokensTable,
            EventDefinitionsTable,
            EventSeriesTable,
            EventInstancesTable,
            ReservationsTable
        )
    }
}