package cz.svitaninymburk.projects.reservations.settings

import cz.svitaninymburk.projects.reservations.repository.settings.AppSettingsRepository
import cz.svitaninymburk.projects.reservations.repository.settings.InMemoryAppSettingsRepository

/**
 * Provides access to the current [AppSettings].
 *
 * Use the primary constructor in production; use [forTest] in unit tests to bypass the
 * SETTINGS_ENCRYPTION_KEY environment-variable requirement.
 */
class AppSettingsProvider private constructor(
    private val repo: AppSettingsRepository,
    initialSettings: AppSettings,
) {
    @Volatile var current: AppSettings = initialSettings
        private set

    /** Production constructor — validates that SETTINGS_ENCRYPTION_KEY is set. */
    constructor(repo: AppSettingsRepository) : this(
        repo = repo,
        initialSettings = initAndLoad(repo),
    )

    fun reload() {
        current = repo.load()
    }

    companion object {
        private fun initAndLoad(repo: AppSettingsRepository): AppSettings {
            check(System.getenv("SETTINGS_ENCRYPTION_KEY") != null) {
                "SETTINGS_ENCRYPTION_KEY env var must be set before starting the application"
            }
            repo.seedIfEmpty(
                AppSettings(
                    bankAccountNumber = System.getenv("BANK_ACCOUNT_NUMBER") ?: "2003487968/2010",
                    fioToken = System.getenv("FIO_TOKEN") ?: "",
                    senderEmail = System.getenv("GMAIL_USERNAME") ?: "",
                    gmailAppPassword = System.getenv("GMAIL_APP_PASSWORD") ?: "",
                    senderDisplayName = "Rodinné centrum Svítání",
                )
            )
            return repo.load()
        }

        /**
         * Creates an [AppSettingsProvider] for use in unit tests, bypassing the
         * SETTINGS_ENCRYPTION_KEY environment-variable requirement.
         */
        fun forTest(settings: AppSettings): AppSettingsProvider =
            AppSettingsProvider(InMemoryAppSettingsRepository(settings), settings)
    }
}
