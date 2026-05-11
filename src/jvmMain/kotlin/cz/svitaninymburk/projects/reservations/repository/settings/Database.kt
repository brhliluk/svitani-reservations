package cz.svitaninymburk.projects.reservations.repository.settings

import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.util.SettingsEncryption
import cz.svitaninymburk.projects.reservations.util.dbQuery
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object AppSettingsTable : Table("app_settings") {
    val id = integer("id").default(1)
    val bankAccountNumber = text("bank_account_number")
    val fioTokenEncrypted = text("fio_token_encrypted")
    val senderEmail = text("sender_email")
    val gmailAppPasswordEncrypted = text("gmail_app_password_encrypted")
    val senderDisplayName = text("sender_display_name")
    override val primaryKey = PrimaryKey(id)
}

class AppSettingsRepository {
    fun load(): AppSettings = transaction {
        AppSettingsTable.selectAll().where { AppSettingsTable.id eq 1 }.single().let { row ->
            AppSettings(
                bankAccountNumber = row[AppSettingsTable.bankAccountNumber],
                fioToken = SettingsEncryption.decrypt(row[AppSettingsTable.fioTokenEncrypted]),
                senderEmail = row[AppSettingsTable.senderEmail],
                gmailAppPassword = SettingsEncryption.decrypt(row[AppSettingsTable.gmailAppPasswordEncrypted]),
                senderDisplayName = row[AppSettingsTable.senderDisplayName],
            )
        }
    }

    fun seedIfEmpty(defaults: AppSettings): Unit = transaction {
        val exists = AppSettingsTable.selectAll().where { AppSettingsTable.id eq 1 }.count() > 0L
        if (!exists) {
            AppSettingsTable.insert {
                it[id] = 1
                it[bankAccountNumber] = defaults.bankAccountNumber
                it[fioTokenEncrypted] = SettingsEncryption.encrypt(defaults.fioToken)
                it[senderEmail] = defaults.senderEmail
                it[gmailAppPasswordEncrypted] = SettingsEncryption.encrypt(defaults.gmailAppPassword)
                it[senderDisplayName] = defaults.senderDisplayName
            }
        }
    }

    suspend fun save(settings: AppSettings): Unit = dbQuery {
        val exists = AppSettingsTable.selectAll().where { AppSettingsTable.id eq 1 }.count() > 0L
        if (exists) {
            AppSettingsTable.update({ AppSettingsTable.id eq 1 }) {
                it[bankAccountNumber] = settings.bankAccountNumber
                it[fioTokenEncrypted] = SettingsEncryption.encrypt(settings.fioToken)
                it[senderEmail] = settings.senderEmail
                it[gmailAppPasswordEncrypted] = SettingsEncryption.encrypt(settings.gmailAppPassword)
                it[senderDisplayName] = settings.senderDisplayName
            }
        } else {
            AppSettingsTable.insert {
                it[id] = 1
                it[bankAccountNumber] = settings.bankAccountNumber
                it[fioTokenEncrypted] = SettingsEncryption.encrypt(settings.fioToken)
                it[senderEmail] = settings.senderEmail
                it[gmailAppPasswordEncrypted] = SettingsEncryption.encrypt(settings.gmailAppPassword)
                it[senderDisplayName] = settings.senderDisplayName
            }
        }
    }
}

class InMemoryAppSettingsRepository(private var stored: AppSettings) {
    fun load(): AppSettings = stored
    fun seedIfEmpty(defaults: AppSettings) { /* no-op: constructor already provides data */ }
    suspend fun save(settings: AppSettings) { stored = settings }
}
