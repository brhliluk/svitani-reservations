package cz.svitaninymburk.projects.reservations.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import cz.svitaninymburk.projects.reservations.repository.auth.RefreshTokensTable
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventInstancesTable
import cz.svitaninymburk.projects.reservations.repository.event.EventOwnerEmailsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesTable
import cz.svitaninymburk.projects.reservations.repository.payment.PaymentEventsTable
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.settings.AppSettingsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import io.ktor.server.application.*
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

fun Application.configureDatabases() {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:sqlite:${System.getenv("DB_PATH") ?: "reservations.db"}?journal_mode=WAL&busy_timeout=5000"
        driverClassName = "org.sqlite.JDBC"
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_SERIALIZABLE"
    }

    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    transaction {
        // Data migration runs first (before MigrationUtils can drop old columns)
        try {
            val alreadyMigrated = EventOwnerEmailsTable.selectAll().count() > 0L
            if (!alreadyMigrated) {
                listOf(
                    "event_definitions" to "definition",
                    "event_series" to "series",
                    "event_instances" to "instance",
                ).forEach { (tableName, entityType) ->
                    exec("SELECT id, lector_email FROM $tableName WHERE lector_email IS NOT NULL AND lector_email != ''") { rs ->
                        while (rs.next()) {
                            val entityId = Uuid.parse(rs.getString("id"))
                            val email = rs.getString("lector_email")
                            EventOwnerEmailsTable.insert {
                                it[EventOwnerEmailsTable.entityType] = entityType
                                it[EventOwnerEmailsTable.entityId] = entityId
                                it[EventOwnerEmailsTable.email] = email
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("⚠️ lector_email migration failed (non-fatal, data may need manual migration): ${e.message}")
        }

        SchemaUtils.create(
            UsersTable,
            RefreshTokensTable,
            EventDefinitionsTable,
            EventSeriesTable,
            EventInstancesTable,
            EventOwnerEmailsTable,
            ReservationsTable,
            AppSettingsTable,
            PaymentEventsTable
        )
        MigrationUtils.statementsRequiredForDatabaseMigration(
            UsersTable,
            RefreshTokensTable,
            EventDefinitionsTable,
            EventSeriesTable,
            EventInstancesTable,
            EventOwnerEmailsTable,
            ReservationsTable,
            AppSettingsTable,
            PaymentEventsTable,
            withLogs = false
        ).forEach { exec(it) }
    }
}