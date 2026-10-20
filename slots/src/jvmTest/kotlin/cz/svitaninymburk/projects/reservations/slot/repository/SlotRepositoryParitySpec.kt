package cz.svitaninymburk.projects.reservations.slot.repository

import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.slot.AvailabilityException
import cz.svitaninymburk.projects.reservations.slot.AvailabilityRule
import cz.svitaninymburk.projects.reservations.slot.SlotService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Stejné scénáře proti SQLite i InMemory. Služby modulu se budou testovat nad InMemory,
 * takže každý rozdíl tady znamená testy, které procházejí na něčem jiném než produkce.
 */
class SlotRepositoryParitySpec {

    private class Repos(
        val name: String,
        val services: SlotServiceRepository,
        val rules: AvailabilityRuleRepository,
        val exceptions: AvailabilityExceptionRepository,
    )

    private fun allRepos(): List<Repos> {
        val dbFile = File.createTempFile("slot-repository-parity", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction { SchemaUtils.create(*SLOT_TABLES.toTypedArray()) }
        val store = InMemorySlotStore()
        return listOf(
            Repos("sqlite", ExposedSlotServiceRepository(), ExposedAvailabilityRuleRepository(), ExposedAvailabilityExceptionRepository()),
            Repos("inMemory", InMemorySlotServiceRepository(store), InMemoryAvailabilityRuleRepository(store), InMemoryAvailabilityExceptionRepository(store)),
        )
    }

    private fun service(title: String = "Hlídání", isPublished: Boolean = true) = SlotService(
        id = Uuid.random(),
        title = title,
        description = "Popis",
        step = 15.minutes,
        minDuration = 1.hours,
        maxDuration = 4.hours + 30.minutes,
        pricePerHour = 137.5,
        capacity = 8,
        maxSeatsPerBooking = 3,
        allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
        customFields = listOf(TextFieldDefinition("children", "Jména a věk dětí", isRequired = true, isMultiline = true)),
        ownerEmails = listOf("hlidani@svitani.cz", "vedouci@svitani.cz"),
        reservationDeadline = 90.minutes,
        bookingHorizon = 28.days,
        isPublished = isPublished,
    )

    private fun rule(serviceId: Uuid, day: DayOfWeek, fromHour: Int, toHour: Int, validTo: LocalDate? = null) =
        AvailabilityRule(Uuid.random(), serviceId, day, LocalTime(fromHour, 0), LocalTime(toHour, 30), LocalDate(2026, 10, 1), validTo)

    @Test
    fun `service round trips with all fields`() = runBlocking {
        for (repos in allRepos()) {
            val withDeadline = repos.services.create(service())
            val withoutDeadline = repos.services.create(service().copy(reservationDeadline = null, customFields = emptyList()))
            val unlimited = repos.services.create(service().copy(step = 60.minutes, minDuration = Duration.ZERO, maxDuration = null))

            assertEquals(withDeadline, repos.services.get(withDeadline.id), repos.name)
            assertEquals(withoutDeadline, repos.services.get(withoutDeadline.id), repos.name)
            assertEquals(unlimited, repos.services.get(unlimited.id), repos.name)
            assertNull(repos.services.get(Uuid.random()), repos.name)
        }
    }

    @Test
    fun `listing is sorted by title and published filter hides drafts`() = runBlocking {
        for (repos in allRepos()) {
            val b = repos.services.create(service("B", isPublished = true))
            val a = repos.services.create(service("A", isPublished = false))
            val c = repos.services.create(service("C", isPublished = true))

            assertEquals(listOf(a, b, c), repos.services.getAll(), repos.name)
            assertEquals(listOf(b, c), repos.services.getAllPublished(), repos.name)
        }
    }

    @Test
    fun `update overwrites service`() = runBlocking {
        for (repos in allRepos()) {
            val original = repos.services.create(service())
            val changed = original.copy(title = "Nové", capacity = 5, isPublished = false, ownerEmails = emptyList())

            repos.services.update(changed)

            assertEquals(changed, repos.services.get(original.id), repos.name)
        }
    }

    @Test
    fun `delete removes service with its rules and exceptions`() = runBlocking {
        for (repos in allRepos()) {
            val deleted = repos.services.create(service())
            val kept = repos.services.create(service())
            repos.rules.replaceForService(deleted.id, listOf(rule(deleted.id, DayOfWeek.TUESDAY, 8, 12)))
            repos.rules.replaceForService(kept.id, listOf(rule(kept.id, DayOfWeek.TUESDAY, 8, 12)))
            repos.exceptions.save(AvailabilityException(Uuid.random(), deleted.id, LocalDate(2026, 10, 20), closed = true))

            assertTrue(repos.services.delete(deleted.id), repos.name)
            assertFalse(repos.services.delete(deleted.id), repos.name)

            assertNull(repos.services.get(deleted.id), repos.name)
            assertTrue(repos.rules.findByService(deleted.id).isEmpty(), repos.name)
            assertTrue(repos.exceptions.findByService(deleted.id).isEmpty(), repos.name)
            assertEquals(1, repos.rules.findByService(kept.id).size, repos.name)
        }
    }

    @Test
    fun `rules are replaced as a whole and sorted by day and time`() = runBlocking {
        for (repos in allRepos()) {
            val svc = repos.services.create(service())
            val other = repos.services.create(service())
            val otherRule = rule(other.id, DayOfWeek.MONDAY, 8, 9)
            repos.rules.replaceForService(other.id, listOf(otherRule))
            repos.rules.replaceForService(svc.id, listOf(rule(svc.id, DayOfWeek.FRIDAY, 8, 9)))

            val thursday = rule(svc.id, DayOfWeek.THURSDAY, 8, 12, validTo = LocalDate(2026, 12, 31))
            val tuesdayAfternoon = rule(svc.id, DayOfWeek.TUESDAY, 14, 16)
            val tuesdayMorning = rule(svc.id, DayOfWeek.TUESDAY, 8, 12)
            val saved = repos.rules.replaceForService(svc.id, listOf(thursday, tuesdayAfternoon, tuesdayMorning))

            assertEquals(listOf(tuesdayMorning, tuesdayAfternoon, thursday), saved, repos.name)
            assertEquals(saved, repos.rules.findByService(svc.id), repos.name)
            assertEquals(listOf(otherRule), repos.rules.findByService(other.id), repos.name)

            assertTrue(repos.rules.replaceForService(svc.id, emptyList()).isEmpty(), repos.name)
        }
    }

    @Test
    fun `exceptions are filtered by inclusive date range and upserted by id`() = runBlocking {
        for (repos in allRepos()) {
            val svc = repos.services.create(service())
            val other = repos.services.create(service())
            val oct20 = AvailabilityException(Uuid.random(), svc.id, LocalDate(2026, 10, 20), closed = true)
            val oct22 = AvailabilityException(Uuid.random(), svc.id, LocalDate(2026, 10, 22), closed = false, from = LocalTime(9, 0), to = LocalTime(11, 30))
            val nov3 = AvailabilityException(Uuid.random(), svc.id, LocalDate(2026, 11, 3), closed = true)
            listOf(nov3, oct20, oct22).forEach { repos.exceptions.save(it) }
            repos.exceptions.save(AvailabilityException(Uuid.random(), other.id, LocalDate(2026, 10, 22), closed = true))

            assertEquals(listOf(oct20, oct22, nov3), repos.exceptions.findByService(svc.id), repos.name)
            assertEquals(listOf(oct22, nov3), repos.exceptions.findByService(svc.id, from = LocalDate(2026, 10, 21)), repos.name)
            assertEquals(listOf(oct20, oct22), repos.exceptions.findByService(svc.id, to = LocalDate(2026, 10, 22)), repos.name)
            assertEquals(listOf(oct22), repos.exceptions.findByService(svc.id, LocalDate(2026, 10, 22), LocalDate(2026, 10, 22)), repos.name)

            val reopened = oct20.copy(closed = false, from = LocalTime(13, 0), to = LocalTime(15, 0))
            repos.exceptions.save(reopened)
            assertEquals(listOf(reopened, oct22, nov3), repos.exceptions.findByService(svc.id), repos.name)

            assertTrue(repos.exceptions.delete(oct22.id), repos.name)
            assertFalse(repos.exceptions.delete(oct22.id), repos.name)
            assertEquals(listOf(reopened, nov3), repos.exceptions.findByService(svc.id), repos.name)
        }
    }
}
