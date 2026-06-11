package cz.svitaninymburk.projects.reservations.android.repository

import android.content.SharedPreferences
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.reservation.ReservationsRepositoryImpl
import cz.svitaninymburk.projects.reservations.api.ApiError
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

class ReservationsRepositoryCreateTest {

    private val instanceId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val seriesId = Uuid.parse("00000000-0000-0000-0000-000000000003")

    private fun reservation(reference: Reference = Reference.Instance(instanceId)) = Reservation(
        id = Uuid.parse("00000000-0000-0000-0000-000000000002"),
        reference = reference,
        contactName = "Jan Novák",
        contactEmail = "jan@example.com",
        contactPhone = "+420123456789",
        seatCount = 2,
        totalPrice = 600.0,
        status = Reservation.Status.PENDING_PAYMENT,
        createdAt = Instant.parse("2026-06-10T10:00:00Z"),
        customValues = emptyMap(),
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
        variableSymbol = "1234567890",
    )

    private fun instanceRequest() = CreateInstanceReservationRequest(
        eventInstanceId = instanceId,
        seatCount = 2,
        contactName = "Jan Novák",
        contactEmail = "jan@example.com",
        contactPhone = "+420123456789",
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
        customValues = emptyMap(),
    )

    private fun client(handler: MockRequestHandler) = HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json(json) }
    }

    private fun repo(handler: MockRequestHandler) =
        ReservationsRepositoryImpl(client(handler), FakePrefs())

    @Test
    fun `createInstanceReservation posts request with bearer token and parses reservation`() = runTest {
        val expected = reservation()
        val repo = repo { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/reservations/instance", request.url.encodedPath)
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            val sent = json.decodeFromString<CreateInstanceReservationRequest>(
                request.body.toByteArray().decodeToString()
            )
            assertEquals(instanceId, sent.eventInstanceId)
            assertEquals(2, sent.seatCount)
            respond(
                content = json.encodeToString(expected),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = repo.createInstanceReservation(instanceRequest())

        assertTrue(result.isRight())
        assertEquals(expected.id, result.getOrNull()!!.id)
    }

    @Test
    fun `createSeriesReservation posts to series endpoint`() = runTest {
        val expected = reservation(reference = Reference.Series(seriesId))
        val repo = repo { request ->
            assertEquals("/api/v1/reservations/series", request.url.encodedPath)
            respond(
                content = json.encodeToString(expected),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = repo.createSeriesReservation(
            CreateSeriesReservationRequest(
                eventSeriesId = seriesId,
                seatCount = 1,
                contactName = "Jan Novák",
                contactEmail = "jan@example.com",
                contactPhone = "+420123456789",
                paymentType = PaymentInfo.Type.ON_SITE,
                customValues = emptyMap(),
            )
        )

        assertTrue(result.isRight())
    }

    @Test
    fun `createInstanceReservation maps ApiError to Server error`() = runTest {
        val repo = repo {
            respond(
                content = json.encodeToString(ApiError("EventInstanceIsFull", "Kapacita je vyčerpána")),
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = repo.createInstanceReservation(instanceRequest())

        val error = result.leftOrNull()
        assertIs<RepositoryError.Server>(error)
        assertEquals("EventInstanceIsFull", error.code)
    }
}

/** SharedPreferences je interface — čistě kotlinová fake, žádné Robolectric. */
private class FakePrefs : SharedPreferences {
    override fun getString(key: String?, defValue: String?): String? =
        if (key == "access_token") "token" else defValue
    override fun getAll(): MutableMap<String, *> = throw UnsupportedOperationException()
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        throw UnsupportedOperationException()
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun contains(key: String?): Boolean = key == "access_token"
    override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
