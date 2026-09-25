package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.repository.settings.InMemoryAppSettingsRepository
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsServiceTest {

    private val settings = AppSettings(
        bankAccountNumber = "2800981651/2010",
        fioToken = "stored-token",
        senderEmail = "svitani@test.com",
        gmailAppPassword = "app-password",
        senderDisplayName = "Rodinné centrum Svítání",
    )

    @Test
    fun `fio test only reads transactions and never moves the download marker`() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedPaths += request.url.encodedPath
            respond(content = "{}", status = HttpStatusCode.OK)
        }
        val service = AppSettingsService(
            repo = InMemoryAppSettingsRepository(settings),
            provider = AppSettingsProvider.forTest(settings),
            httpClient = HttpClient(engine),
        )

        val result = service.testFioSettings(fioToken = null)

        assertTrue(result.isRight())
        assertEquals(1, requestedPaths.size)
        val path = requestedPaths.single()
        assertTrue(path.startsWith("/v1/rest/periods/stored-token/"), path)
        assertFalse(path.contains("set-last-date"), path)
    }

    @Test
    fun `fio test reports http error`() = runBlocking {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.Conflict) }
        val service = AppSettingsService(
            repo = InMemoryAppSettingsRepository(settings),
            provider = AppSettingsProvider.forTest(settings),
            httpClient = HttpClient(engine),
        )

        assertTrue(service.testFioSettings(fioToken = "new-token").isLeft())
    }
}
