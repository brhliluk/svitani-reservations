package cz.svitaninymburk.projects.reservations.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.deduplicateFieldKeys
import cz.svitaninymburk.projects.reservations.event.remapValuesAfterKeyDeduplication
import cz.svitaninymburk.projects.reservations.repository.auth.RefreshTokensTable
import cz.svitaninymburk.projects.reservations.repository.claim.ReservationClaimTokensTable
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
import cz.svitaninymburk.projects.reservations.repository.audit.AuditEventsTable
import cz.svitaninymburk.projects.reservations.repository.wallet.WalletTransactionsTable
import cz.svitaninymburk.projects.reservations.repository.wallet.WalletsTable
import io.ktor.server.application.*
import java.nio.ByteBuffer
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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

    // Přestavba klíče musí proběhnout ve vlastní transakci: SQLite umí DDL
    // transakčně, takže případný pád vrátí celou přestavbu zpět a další start
    // ji zkusí znovu z čistého stavu. Uvnitř sdílené transakce by se rozdělaný
    // stav commitnul se zbytkem bootu.
    try {
        transaction { migrateAttendanceToPerLessonKey() }
    } catch (e: Exception) {
        // Ve skutečnosti to fatální je: nedokončená přestavba nechá tabulku beze
        // sloupce instance_id, na což o pár řádků níž narazí MigrationUtils a zkusí
        // "ALTER TABLE ... ADD COLUMN instance_id ... NOT NULL" bez výchozí hodnoty —
        // to SQLite odmítne a start shodí s nesouvisející SQLiteException. Proto tu
        // chybu nahlas zalogujeme a start ukončíme rovnou, s jasnou příčinou v logu.
        println("‼️ Přestavba klíče reservation_attendance selhala — APLIKACE SE NESPUSTÍ, dokud DB nebude opravena:")
        e.printStackTrace()
        throw e
    }

    transaction {
        // Data migration runs first (before MigrationUtils can drop old columns)
        nonFatal("lector_email migration (data may need manual migration)") {
            migrateLectorEmailsToOwnerEmails()
        }

        val instancesPublishMissing = !columnExists("event_instances", "is_published")
        val seriesPublishMissing = !columnExists("event_series", "is_published")

        // Musí doběhnout PŘED MigrationUtils — ty nově zakládají unique index na
        // (reservation_id, instance_id) a na datech s duplicitou by DDL selhalo
        // a shodilo start aplikace.
        nonFatal("series_lesson_opt_outs deduplication") {
            val smazano = deduplicateSeriesLessonOptOuts()
            if (smazano > 0) {
                println("ℹ️ omluvenky z lekcí: $smazano duplicitních záznamů odstraněno")
            }
        }

        SchemaUtils.create(*ALL_TABLES)
        MigrationUtils.statementsRequiredForDatabaseMigration(*ALL_TABLES, withLogs = false).forEach { exec(it) }

        nonFatal("is_published backfill") {
            backfillPublishedIfFirstRun("event_instances", instancesPublishMissing)
            backfillPublishedIfFirstRun("event_series", seriesPublishMissing)
        }

        nonFatal("custom_fields backfill") {
            backfillCustomFieldsFromTemplates()
        }

        // Až po MigrationUtils — ty sloupec refunded_amount teprve zakládají.
        nonFatal("series_lesson_opt_outs refunded_amount backfill") {
            val doplneno = backfillOptOutRefundedAmounts()
            if (doplneno > 0) {
                println("ℹ️ omluvenky z lekcí: u $doplneno historických záznamů dohledána vyplacená částka")
            }
        }

        // Až po MigrationUtils — ty sloupec lesson_share teprve zakládají.
        nonFatal("reservations lesson_share backfill") {
            val doplneno = backfillLessonShares()
            if (doplneno > 0) {
                println("ℹ️ zápisy na kurz: u $doplneno historických rezervací doplněna poměrná část ceny za lekci")
            }
        }

        nonFatal("custom_fields key deduplication") {
            deduplicateCustomFieldKeys()
        }

        nonFatal("free reservations backfill") {
            val potvrzenych = confirmFreeReservations()
            if (potvrzenych > 0) {
                println("ℹ️ rezervace zdarma: $potvrzenych historických rezervací přepsáno na CONFIRMED/FREE")
            }
        }

        // Musí doběhnout synchronně tady, ne asynchronně po startu — jinak by mohl
        // závodit s mock loaderem (přepsal by jím nasazená testovací data) nebo
        // s první příchozí rezervací (viz komentář u recomputeOccupiedSpotsInTransaction
        // v OccupancyBackfill.kt). Dvě UPDATE na málo řádků, na latenci startu to nic
        // nepřidá.
        nonFatal("occupancy backfill") {
            recomputeOccupiedSpotsInTransaction()
        }
    }
}

/** Obě volání (create i migrace) musí znát stejné tabulky — chybějící by se tiše přeskočila. */
private val ALL_TABLES = arrayOf<Table>(
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
    AuditEventsTable,
    ReservationClaimTokensTable,
)

/** Krok, který nesmí shodit start: chyba se zaloguje a start pokračuje. */
private inline fun nonFatal(step: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        println("⚠️ $step failed (non-fatal): ${e.message}")
    }
}

internal fun JdbcTransaction.columnExists(table: String, column: String): Boolean =
    exec("SELECT count(*) FROM pragma_table_info('$table') WHERE name = '$column'") { rs ->
        rs.next() && rs.getInt(1) > 0
    } ?: false

internal fun JdbcTransaction.tableExists(table: String): Boolean =
    exec("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '$table'") { rs ->
        rs.next() && rs.getInt(1) > 0
    } ?: false

/**
 * Prezence se dřív klíčovala jen na rezervaci. Účastník kurzu má jedinou rezervaci
 * napříč všemi lekcemi série, takže se klíč rozšiřuje na (rezervace, lekce).
 *
 * SQLite primární klíč nezmění, proto rebuild tabulky. Historická docházka se
 * mapuje na instanci z `reference_id` své rezervace; řádky u rezervací na sérii
 * se namapovat nedají a zahazují se. Idempotentní: existuje-li už `instance_id`,
 * nedělá nic; na čerstvé DB bez tabulky je no-op.
 *
 * Volající musí tuto funkci spustit ve vlastní transakci, ne ve sdílené transakci
 * bootu — jinak by se při chybě mezi DROP a RENAME rozdělaný stav commitnul a
 * historická docházka by zůstala osiřelá v `reservation_attendance_new`.
 */
internal fun JdbcTransaction.migrateAttendanceToPerLessonKey() {
    // Čerstvá instalace zakládá reservation_attendance až SchemaUtils.create níže —
    // tady na ní ještě nic není co přestavovat.
    if (!tableExists("reservation_attendance")) return
    if (columnExists("reservation_attendance", "instance_id")) return

    // Zbytek po dřívějším nedokončeném pokusu (např. z doby, než tahle migrace
    // běžela ve vlastní transakci) by jinak CREATE TABLE níže shodil na "table
    // already exists" a migrace by se zablokovala natrvalo.
    exec("DROP TABLE IF EXISTS reservation_attendance_new")

    exec("""
        CREATE TABLE reservation_attendance_new (
            reservation_id BINARY(16) NOT NULL,
            instance_id BINARY(16) NOT NULL,
            checked_in INTEGER NOT NULL DEFAULT 0,
            checked_in_at TEXT NULL,
            CONSTRAINT pk_reservation_attendance PRIMARY KEY (reservation_id, instance_id),
            CONSTRAINT fk_reservation_attendance_reservation
                FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE
        )
    """.trimIndent())

    exec("""
        INSERT INTO reservation_attendance_new (reservation_id, instance_id, checked_in, checked_in_at)
        SELECT a.reservation_id, r.reference_id, a.checked_in, a.checked_in_at
          FROM reservation_attendance a
          JOIN reservations r ON r.id = a.reservation_id
         WHERE r.reference_type = 'INSTANCE'
    """.trimIndent())

    val prenesenych = exec("SELECT count(*) FROM reservation_attendance_new") { rs ->
        rs.next(); rs.getInt(1)
    } ?: 0
    val puvodnich = exec("SELECT count(*) FROM reservation_attendance") { rs ->
        rs.next(); rs.getInt(1)
    } ?: 0
    if (puvodnich > prenesenych) {
        println("⚠️ reservation_attendance: zahozeno ${puvodnich - prenesenych} nenamapovatelných řádků docházky")
    }

    exec("DROP TABLE reservation_attendance")
    exec("ALTER TABLE reservation_attendance_new RENAME TO reservation_attendance")
}

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
            if (!columnExists(tableName, "lector_email")) return@forEach

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

// Kurzy/termíny vytvořené před opravou dědění vlastních polí ze šablony mají
// custom_fields prázdné, i když šablona pole má. Idempotentní: dobarví jen řádky,
// které jsou zatím prázdné, takže opakovaný běh nic nepřepíše.
internal fun JdbcTransaction.backfillCustomFieldsFromTemplates() {
    val definitionFieldsById = EventDefinitionsTable.selectAll()
        .associate { it[EventDefinitionsTable.id] to it[EventDefinitionsTable.customFields] }

    EventSeriesTable.selectAll().forEach { row ->
        if (row[EventSeriesTable.customFields].isEmpty()) {
            val templateFields = definitionFieldsById[row[EventSeriesTable.definitionId]]
            if (!templateFields.isNullOrEmpty()) {
                EventSeriesTable.update({ EventSeriesTable.id eq row[EventSeriesTable.id] }) {
                    it[customFields] = templateFields
                }
            }
        }
    }

    val seriesFieldsById = EventSeriesTable.selectAll()
        .associate { it[EventSeriesTable.id] to it[EventSeriesTable.customFields] }

    EventInstancesTable.selectAll().forEach { row ->
        if (row[EventInstancesTable.customFields].isEmpty()) {
            val seriesId = row[EventInstancesTable.seriesId]
            val templateFields = if (seriesId != null) {
                seriesFieldsById[seriesId]
            } else {
                definitionFieldsById[row[EventInstancesTable.definitionId]]
            }
            if (!templateFields.isNullOrEmpty()) {
                EventInstancesTable.update({ EventInstancesTable.id eq row[EventInstancesTable.id] }) {
                    it[customFields] = templateFields
                }
            }
        }
    }
}

// Události vytvořené předtím, než se klíče vlastních polí začaly generovat přes
// nextAvailableFieldKey (klíč se počítal jako "field_${customFields.size}", takže po
// smazání pole a přidání nového vznikla duplicita), mají v custom_fields dvě pole se
// stejným key. Hodnoty rezervace jsou mapované právě přes key, takže si taková pole
// v rezervačním formuláři navzájem přepisují hodnotu. Dedup v admin builderu opraví
// jen stav formuláře — dokud admin neuloží, v DB duplicita zůstane.
//
// Idempotentní: řádky s unikátními klíči nechá být. Pro přejmenování nepoužije klíč,
// který se už vyskytuje v hodnotách existujících rezervací na daný termín/kurz, aby
// opravené pole nezdědilo hodnotu po dávno smazaném poli.
/**
 * Nechá na každou dvojici (rezervace, lekce) jen nejstarší omluvenku. Duplicity
 * mohly vzniknout dvojklikem, než na tabulce byl unique index — a bez jejich
 * odstranění by se ten index nedal založit. Vrací počet smazaných řádků.
 */
internal fun JdbcTransaction.deduplicateSeriesLessonOptOuts(): Int {
    // Tabulka nemusí ještě existovat (první spuštění) — pak není co dedupovat.
    if (!tableExists("series_lesson_opt_outs")) return 0

    val keepOldest = """
        SELECT MIN(rowid) FROM series_lesson_opt_outs
        GROUP BY reservation_id, instance_id
    """.trimIndent()

    val duplicates = exec(
        "SELECT count(*) FROM series_lesson_opt_outs WHERE rowid NOT IN ($keepOldest)"
    ) { rs -> if (rs.next()) rs.getInt(1) else 0 } ?: 0

    if (duplicates > 0) {
        exec("DELETE FROM series_lesson_opt_outs WHERE rowid NOT IN ($keepOldest)")
    }
    return duplicates
}

internal fun JdbcTransaction.deduplicateCustomFieldKeys() {
    val reservedKeysByReference = mutableMapOf<Uuid, MutableSet<String>>()
    ReservationsTable
        .select(ReservationsTable.referenceId, ReservationsTable.customValues)
        .forEach { row ->
            reservedKeysByReference
                .getOrPut(row[ReservationsTable.referenceId]) { mutableSetOf() }
                .addAll(row[ReservationsTable.customValues].keys)
        }

    EventDefinitionsTable.selectAll().forEach { row ->
        val fields = row[EventDefinitionsTable.customFields]
        val deduped = deduplicateFieldKeys(fields)
        if (deduped != fields) {
            EventDefinitionsTable.update({ EventDefinitionsTable.id eq row[EventDefinitionsTable.id] }) {
                it[customFields] = deduped
            }
        }
    }

    EventSeriesTable.selectAll().forEach { row ->
        val id = row[EventSeriesTable.id]
        val fields = row[EventSeriesTable.customFields]
        val deduped = deduplicateFieldKeys(fields, reservedKeysByReference[id].orEmpty())
        if (deduped != fields) {
            EventSeriesTable.update({ EventSeriesTable.id eq id }) {
                it[customFields] = deduped
            }
            remapReservationValues(id, fields, deduped)
        }
    }

    EventInstancesTable.selectAll().forEach { row ->
        val id = row[EventInstancesTable.id]
        val fields = row[EventInstancesTable.customFields]
        val seriesReservedKeys = row[EventInstancesTable.seriesId]
            ?.let { reservedKeysByReference[it] }
            .orEmpty()
        val deduped = deduplicateFieldKeys(fields, reservedKeysByReference[id].orEmpty() + seriesReservedKeys)
        if (deduped != fields) {
            EventInstancesTable.update({ EventInstancesTable.id eq id }) {
                it[customFields] = deduped
            }
            remapReservationValues(id, fields, deduped)
        }
    }
}

// Hodnoty už odeslaných rezervací jsou mapované přes key pole, takže po přejmenování
// klíče by odpověď zůstala viset u pole, kterému nepatří (a v detailu rezervace by se
// zobrazila jako prázdná). Přesune ji na pole, jehož typ jí odpovídá.
private fun JdbcTransaction.remapReservationValues(
    referenceId: Uuid,
    originalFields: List<CustomFieldDefinition>,
    dedupedFields: List<CustomFieldDefinition>,
) {
    ReservationsTable.selectAll()
        .where { ReservationsTable.referenceId eq referenceId }
        .forEach { row ->
            val values = row[ReservationsTable.customValues]
            val remapped = remapValuesAfterKeyDeduplication(originalFields, dedupedFields, values)
            if (remapped != values) {
                ReservationsTable.update({ ReservationsTable.id eq row[ReservationsTable.id] }) {
                    it[customValues] = remapped
                }
            }
        }
}
