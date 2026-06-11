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
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutsTable
import cz.svitaninymburk.projects.reservations.repository.settings.AppSettingsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.repository.attendance.ReservationAttendanceTable
import cz.svitaninymburk.projects.reservations.repository.wallet.WalletTransactionsTable
import cz.svitaninymburk.projects.reservations.repository.wallet.WalletsTable
import io.ktor.server.application.*
import java.nio.ByteBuffer
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
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
            migrateLectorEmailsToOwnerEmails()
        } catch (e: Exception) {
            println("⚠️ lector_email migration failed (non-fatal, data may need manual migration): ${e.message}")
        }

        val instancesPublishMissing = !columnExists("event_instances", "is_published")
        val seriesPublishMissing = !columnExists("event_series", "is_published")

        SchemaUtils.create(
            UsersTable,
            RefreshTokensTable,
            EventDefinitionsTable,
            EventSeriesTable,
            EventInstancesTable,
            EventOwnerEmailsTable,
            ReservationsTable,
            AppSettingsTable,
            PaymentEventsTable,
            SeriesLessonOptOutsTable,
            WalletsTable,
            WalletTransactionsTable,
            ReservationAttendanceTable,
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
            SeriesLessonOptOutsTable,
            WalletsTable,
            WalletTransactionsTable,
            ReservationAttendanceTable,
            withLogs = false,
        ).forEach { exec(it) }

        try {
            backfillPublishedIfFirstRun("event_instances", instancesPublishMissing)
            backfillPublishedIfFirstRun("event_series", seriesPublishMissing)
        } catch (e: Exception) {
            println("⚠️ is_published backfill failed (non-fatal): ${e.message}")
        }
    }
}

internal fun JdbcTransaction.columnExists(table: String, column: String): Boolean =
    exec("SELECT count(*) FROM pragma_table_info('$table') WHERE name = '$column'") { rs ->
        rs.next() && rs.getInt(1) > 0
    } ?: false

// Po přidání sloupce is_published zveřejní všechny existující řádky, aby události
// vytvořené před touto funkcí zůstaly veřejně viditelné. Idempotentní: spustí se jen
// v běhu, kdy sloupec předtím neexistoval (wasMissingBefore == true).
internal fun JdbcTransaction.backfillPublishedIfFirstRun(tableName: String, wasMissingBefore: Boolean) {
    if (wasMissingBefore) {
        exec("UPDATE $tableName SET is_published = 1")
    }
}

internal fun JdbcTransaction.migrateLectorEmailsToOwnerEmails() {
    // The target table is normally created later in configureDatabases, so on the
    // first run after deploying this migration it does not exist yet
    SchemaUtils.create(EventOwnerEmailsTable)
    val alreadyMigrated = EventOwnerEmailsTable.selectAll().count() > 0L
    if (!alreadyMigrated) {
        listOf(
            "event_definitions" to "definition",
            "event_series" to "series",
            "event_instances" to "instance",
        ).forEach { (tableName, entityType) ->
            val hasLectorEmailColumn = exec(
                "SELECT count(*) FROM pragma_table_info('$tableName') WHERE name = 'lector_email'"
            ) { rs -> rs.next() && rs.getInt(1) > 0 } ?: false
            if (!hasLectorEmailColumn) return@forEach

            val ownerEmails = mutableListOf<Pair<Uuid, String>>()
            exec("SELECT id, lector_email FROM $tableName WHERE lector_email IS NOT NULL AND lector_email != ''") { rs ->
                while (rs.next()) {
                    // Exposed stores UUIDs in SQLite as 16-byte blobs, but older rows may hold text
                    val entityId = when (val rawId = rs.getObject("id")) {
                        is ByteArray -> ByteBuffer.wrap(rawId).let { Uuid.fromLongs(it.long, it.long) }
                        is String -> Uuid.parse(rawId)
                        else -> error("Unexpected id type in $tableName: ${rawId?.javaClass?.name}")
                    }
                    ownerEmails += entityId to rs.getString("lector_email")
                }
            }
            ownerEmails.forEach { (entityId, email) ->
                EventOwnerEmailsTable.insert {
                    it[EventOwnerEmailsTable.entityType] = entityType
                    it[EventOwnerEmailsTable.entityId] = entityId
                    it[EventOwnerEmailsTable.email] = email
                }
            }
        }
    }
}