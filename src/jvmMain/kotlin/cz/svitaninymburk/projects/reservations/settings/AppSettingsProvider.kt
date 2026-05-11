package cz.svitaninymburk.projects.reservations.settings

import cz.svitaninymburk.projects.reservations.repository.settings.AppSettingsRepository

class AppSettingsProvider(private val repo: AppSettingsRepository) {
    init {
        repo.seedIfEmpty(
            AppSettings(
                bankAccountNumber = System.getenv("BANK_ACCOUNT_NUMBER") ?: "2003487968/2010",
                fioToken = System.getenv("FIO_TOKEN") ?: "",
                senderEmail = System.getenv("GMAIL_USERNAME") ?: "",
                gmailAppPassword = System.getenv("GMAIL_APP_PASSWORD") ?: "",
                senderDisplayName = "Rodinné centrum Svítání",
            )
        )
    }

    @Volatile var current: AppSettings = repo.load()
        private set

    fun reload() {
        current = repo.load()
    }
}
