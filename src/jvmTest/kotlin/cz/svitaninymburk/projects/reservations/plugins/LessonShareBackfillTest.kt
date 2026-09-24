package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventInstancesTable
import cz.svitaninymburk.projects.reservations.repository.event.EventOwnerEmailsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesTable
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReferenceDbDiscriminator
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * Zápisy na kurz z doby před ukládáním `lesson_share` ho nemají — bez doplnění by
 * kurz bez ruční sazby za omluvenku nevracel nic.
 */
class LessonShareBackfillTest {

    private val seriesId = Uuid.random()

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("lesson-share-backfill-test", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(
                UsersTable, EventDefinitionsTable, EventSeriesTable, EventInstancesTable, EventOwnerEmailsTable, ReservationsTable,
            )
        }
        runBlocking {
            val defId = ExposedEventDefinitionRepository().create(
                EventDefinition(
                    id = Uuid.random(), title = "T", description = "D",
                    defaultPrice = 100.0, defaultCapacity = 10, defaultDuration = 1.hours,
                )
            ).id
            ExposedEventSeriesRepository().create(
                EventSeries(
                    id = seriesId, definitionId = defId, title = "Kurz", description = "",
                    price = 1000.0, capacity = 10,
                    startDate = LocalDate(2099, 1, 1), endDate = LocalDate(2099, 1, 3), lessonCount = 3,
                )
            )
            // Tři lekce, z toho jedna zrušená — v době zápisu byla součástí kurzu.
            (1..3).forEach { day ->
                ExposedEventInstanceRepository().create(
                    EventInstance(
                        id = Uuid.random(), definitionId = defId, seriesId = seriesId, title = "Lekce", description = "",
                        startDateTime = LocalDateTime(2099, 1, day, 9, 0), endDateTime = LocalDateTime(2099, 1, day, 10, 0),
                        price = 100.0, capacity = 10, isCancelled = day == 3,
                    )
                )
            }
        }
    }

    private fun insertReservation(referenceType: ReferenceDbDiscriminator, referenceId: Uuid, totalPrice: Double): Uuid {
        val id = Uuid.random()
        transaction {
            ReservationsTable.insert {
                it[ReservationsTable.id] = id
                it[ReservationsTable.referenceId] = referenceId
                it[ReservationsTable.referenceType] = referenceType
                it[contactName] = "Jan Novak"
                it[contactEmail] = "jan@test.com"
                it[seatCount] = 2
                it[ReservationsTable.totalPrice] = totalPrice
                it[status] = Reservation.Status.CONFIRMED
                it[createdAt] = Clock.System.now()
                it[customValues] = emptyMap<String, CustomFieldValue>()
                it[paymentType] = PaymentType.BANK_TRANSFER
            }
        }
        return id
    }

    private fun shareOf(id: Uuid): Double? = transaction {
        ReservationsTable.selectAll().single { it[ReservationsTable.id] == id }[ReservationsTable.lessonShare]
    }

    @Test
    fun `doplni pomernou cast ceny k zapisum na kurz a nesahne na jednorazove akce`() {
        val zapis = insertReservation(ReferenceDbDiscriminator.SERIES, seriesId, totalPrice = 2000.0)
        val akce = insertReservation(ReferenceDbDiscriminator.INSTANCE, Uuid.random(), totalPrice = 500.0)

        val doplneno = transaction { backfillLessonShares() }

        assertEquals(1, doplneno)
        assertEquals(666.0, shareOf(zapis), "2000 Kč ÷ 3 lekce (i se zrušenou), dolů na koruny")
        assertNull(shareOf(akce))
        assertEquals(0, transaction { backfillLessonShares() }, "druhý běh už nic nenajde")
    }
}
