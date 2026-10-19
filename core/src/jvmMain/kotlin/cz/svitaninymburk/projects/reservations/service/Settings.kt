package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import cz.svitaninymburk.projects.reservations.error.SettingsError
import cz.svitaninymburk.projects.reservations.repository.settings.AppSettingsRepository
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsDisplayDto
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.settings.UpdateSettingsRequest
import cz.svitaninymburk.projects.reservations.settings.maskSecret
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.server.util.url
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.simplejavamail.MailException
import org.simplejavamail.email.EmailBuilder

class AppSettingsService(
    private val repo: AppSettingsRepository,
    private val provider: AppSettingsProvider,
    private val httpClient: HttpClient,
) : AppSettingsServiceInterface {

    override suspend fun getSettings(): Either<SettingsError, AppSettingsDisplayDto> = either {
        catch({
            val s = provider.current
            AppSettingsDisplayDto(
                bankAccountNumber = s.bankAccountNumber,
                fioTokenMasked = maskSecret(s.fioToken),
                senderEmail = s.senderEmail,
                gmailPasswordMasked = maskSecret(s.gmailAppPassword),
                senderDisplayName = s.senderDisplayName,
                seasonResetMonth = s.seasonResetMonth,
                seasonResetDay = s.seasonResetDay,
                walletResetWarningDays = s.walletResetWarningDays,
            )
        }) { _: Exception ->
            raise(SettingsError.LoadFailed)
        }
    }

    override suspend fun testEmailSettings(
        senderEmail: String,
        appPassword: String?,
        displayName: String,
    ): Either<SettingsError, Unit> = either {
        val password = appPassword ?: provider.current.gmailAppPassword
        val smtpError: MailException? = withContext(Dispatchers.IO) {
            try {
                val email = EmailBuilder.startingBlank()
                    .from(displayName, senderEmail)
                    .addTo(senderEmail)
                    .withSubject("Test nastavení e-mailu / Email settings test")
                    .withPlainText("Tento e-mail potvrzuje, že nastavení odesílání funguje správně.")
                    .buildEmail()
                gmailMailer(senderEmail, password).sendBlocking(email)
                null
            } catch (e: MailException) {
                e
            }
        }
        if (smtpError != null) raise(SettingsError.EmailTestFailed(smtpError.fullMessage()))
    }

    /**
     * Test smí jen číst. Dřív volal `set-last-date`, který ve Fio posune zarážku
     * posledního stažení — automatické párování (`/last/`) pak dnešní ještě nestažené
     * platby už nedostalo. `periods` za dnešek zarážky nijak nemění.
     */
    override suspend fun testFioSettings(fioToken: String?): Either<SettingsError, Unit> = either {
        val token = fioToken ?: provider.current.fioToken
        val today = LocalDate.now()
        val response = try {
            httpClient.get(url {
                protocol = URLProtocol.HTTPS
                host = "fioapi.fio.cz"
                path("v1/rest/periods/$token/$today/$today/transactions.json")
            })
        } catch (e: Exception) {
            raise(SettingsError.FioTestFailed(e.message ?: "Connection failed"))
        }
        if (!response.status.isSuccess()) {
            raise(SettingsError.FioTestFailed("HTTP ${response.status}"))
        }
    }

    override suspend fun saveSettings(request: UpdateSettingsRequest): Either<SettingsError, Unit> = either {
        val current = provider.current
        val updated = AppSettings(
            bankAccountNumber = request.bankAccountNumber,
            fioToken = request.fioToken ?: current.fioToken,
            senderEmail = request.senderEmail,
            gmailAppPassword = request.gmailAppPassword ?: current.gmailAppPassword,
            senderDisplayName = request.senderDisplayName,
            seasonResetMonth = request.seasonResetMonth,
            seasonResetDay = request.seasonResetDay,
            walletResetWarningDays = request.walletResetWarningDays,
        )
        catch({ repo.save(updated) }) { _: Exception ->
            raise(SettingsError.SaveFailed)
        }
        provider.reload()
    }
}
