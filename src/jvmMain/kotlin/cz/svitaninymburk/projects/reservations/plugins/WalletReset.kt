package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.service.WalletEmailService
import cz.svitaninymburk.projects.reservations.service.WalletService
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.hours

fun Application.startWalletResetJobs() {
    val walletService: WalletService by inject()
    val walletEmailService: WalletEmailService by inject()
    val settingsProvider: AppSettingsProvider by inject()

    launch(Dispatchers.IO) {
        while (isActive) {
            runCatching {
                val settings = settingsProvider.current
                val tz = TimeZone.of("Europe/Prague")
                val today = Clock.System.now().toLocalDateTime(tz).date

                // Season reset
                if (today.monthNumber == settings.seasonResetMonth && today.dayOfMonth == settings.seasonResetDay) {
                    val count = walletService.performSeasonReset()
                    println("Season wallet reset: zeroed $count wallets")
                }

                // Reset warning (N days before reset)
                val thisYearReset = LocalDate(today.year, settings.seasonResetMonth, settings.seasonResetDay)
                val resetDate = if (today <= thisYearReset) thisYearReset
                                else LocalDate(today.year + 1, settings.seasonResetMonth, settings.seasonResetDay)
                val warningDate = resetDate.minus(settings.walletResetWarningDays, DateTimeUnit.DAY)
                if (today == warningDate) {
                    val wallets = walletService.getWalletsForResetWarning()
                    wallets.forEach { wallet ->
                        walletEmailService.sendWalletResetWarning(
                            toEmail = wallet.ownerEmail,
                            walletCode = wallet.code,
                            currentBalance = wallet.balance,
                            resetMonth = settings.seasonResetMonth,
                            resetDay = settings.seasonResetDay,
                            locale = "cs",
                        )
                    }
                    println("Sent wallet reset warnings to ${wallets.size} users")
                }
            }.onFailure { e ->
                println("WalletReset job error: ${e.message}")
            }

            delay(24.hours)
        }
    }
}
