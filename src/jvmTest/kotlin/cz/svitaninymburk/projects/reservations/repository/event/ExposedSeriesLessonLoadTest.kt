package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedSeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * `SeriesLessonLoadTest` pokrývá jen in-memory implementaci. Zátěžová logika
 * v [ExposedSeriesLessonLoad] má ale strukturálně jiný kód — join a agregaci
 * s `groupBy` — bez obdoby jinde v projektu, takže chybu ve extrakci klíče po
 * `groupBy` nebo ve spojovacích sloupcích by in-memory testy neodhalily
 * (prošly by kompilací i testy a vrátily by špatnou obsazenost až na ostrém
 * dashboardu). Tento test proto jede přes reálnou SQLite databázi a ověřuje
 * stejné scénáře jako [SeriesLessonLoadTest], ale skrz [ExposedSeriesLessonLoad].
 */
class ExposedSeriesLessonLoadTest {

    companion object {
        private val dbFile: File = File.createTempFile("series-lesson-load-test", ".db").also { it.deleteOnExit() }

        val db: Database = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC",
        )

        init {
            transaction(db) {
                SchemaUtils.create(UsersTable, ReservationsTable, SeriesLessonOptOutsTable)
            }
        }
    }

    private val definitionId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val reservationRepo = ExposedReservationRepository()
    private val optOutRepo = ExposedSeriesLessonOptOutRepository()
    private val load = ExposedSeriesLessonLoad()

    private fun lesson(id: Uuid, series: Uuid?) = EventInstance(
        id = id,
        definitionId = definitionId,
        seriesId = series,
        title = "Lekce",
        description = "",
        startDateTime = LocalDateTime(2026, 9, 1, 10, 0),
        endDateTime = LocalDateTime(2026, 9, 1, 11, 0),
        price = 100.0,
        capacity = 10,
    )

    private suspend fun enrol(
        series: Uuid,
        seats: Int,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
        id: Uuid = Uuid.random(),
    ): Reservation = reservationRepo.save(
        Reservation(
            id = id,
            reference = Reference.Series(series),
            contactName = "Tester",
            contactEmail = "tester@example.com",
            seatCount = seats,
            totalPrice = 100.0,
            status = status,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.BANK_TRANSFER,
        )
    )

    private suspend fun optOut(reservation: Reservation, instanceId: Uuid) = optOutRepo.save(
        SeriesLessonOptOut(
            id = Uuid.random(),
            reservationId = reservation.id,
            instanceId = instanceId,
            optedOutAt = Clock.System.now(),
            isLateCancellation = false,
        )
    )

    @Test
    fun `dva lekce jedne serie hlasi stejnou spravnou zatez`() = runBlocking {
        val seriesId = Uuid.random()
        val lessonA = lesson(Uuid.random(), seriesId)
        val lessonB = lesson(Uuid.random(), seriesId)
        enrol(seriesId, seats = 2)
        enrol(seriesId, seats = 1)

        val result = load.forInstances(listOf(lessonA, lessonB))

        assertEquals(3, result[lessonA.id])
        assertEquals(3, result[lessonB.id])
    }

    @Test
    fun `omluvenka odecte pocet mist sve rezervace jen na sve lekci`() = runBlocking {
        val seriesId = Uuid.random()
        val lessonA = lesson(Uuid.random(), seriesId)
        val lessonB = lesson(Uuid.random(), seriesId)
        val rodina = enrol(seriesId, seats = 2)
        enrol(seriesId, seats = 1)
        optOut(rodina, lessonA.id)

        val result = load.forInstances(listOf(lessonA, lessonB))

        assertEquals(1, result[lessonA.id], "rodina se dvěma místy se z lekce A omluvila")
        assertEquals(3, result[lessonB.id], "lekce B zůstává nedotčená")
    }

    @Test
    fun `zrusena rezervace s omluvenkou se neodecte dvakrat`() = runBlocking {
        val seriesId = Uuid.random()
        val lessonA = lesson(Uuid.random(), seriesId)
        val zrusena = enrol(seriesId, seats = 2)
        optOut(zrusena, lessonA.id)
        enrol(seriesId, seats = 3)
        reservationRepo.updateStatus(zrusena.id, Reservation.Status.CANCELLED)

        val result = load.forInstances(listOf(lessonA))

        assertEquals(3, result[lessonA.id], "zrušená rezervace už není v součtu, omluvenka ji nesmí odečíst znovu")
    }

    @Test
    fun `prihlaska na jinou serii lekci nezatezuje`() = runBlocking {
        val seriesId = Uuid.random()
        val otherSeriesId = Uuid.random()
        val lessonA = lesson(Uuid.random(), seriesId)
        enrol(otherSeriesId, seats = 5)

        val result = load.forInstances(listOf(lessonA))

        assertEquals(0, result[lessonA.id])
    }
}
