package cz.svitaninymburk.projects.reservations.mock

import cz.svitaninymburk.projects.reservations.auth.HashingService
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.user.UserRepository
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.user.User.Email
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid // Náš nový nejlepší kamarád pro IDčka

class MockDataLoader: KoinComponent {
    val defRepo: EventDefinitionRepository by inject()
    val instRepo: EventInstanceRepository by inject()
    val seriesRepo: EventSeriesRepository by inject()
    val userRepo: UserRepository by inject()
    val hashingService: HashingService by inject()

    val adminUuid = Uuid.random()

    private val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    private val today = now.date

    suspend fun clearAll() {
       defRepo.getAll(null).forEach {
           defRepo.delete(it.id)
       }
        instRepo.getAll(null).forEach {
            instRepo.delete(it.id)
        }
        seriesRepo.getAll(null).forEach {
            seriesRepo.delete(it.id)
        }
        userRepo.delete(adminUuid)
    }

    suspend fun load() {
        userRepo.create(
            Email(
                id = adminUuid,
                email = "admin@reservations.cz",
                name = "Hlavní",
                surname = "Administrátor",
                role = User.Role.ADMIN,
                passwordHash = hashingService.generateSaltedHash("123456"),
            )
        )

        // Vygenerujeme si pevná IDčka pro provázání dat
        val defYogaId = Uuid.random()
        val defCeramicsId = Uuid.random()
        val defPartyId = Uuid.random()
        val seriesCeramicsId = Uuid.random()

        // ==========================================
        // 1. DEFINICE (Šablony s Custom Fields)
        // ==========================================

        val defYoga = EventDefinition(
            id = defYogaId,
            title = "Jóga pro zdravá záda",
            description = "Lekce zaměřená na protažení a posílení středu těla. Vhodné i pro začátečníky.",
            defaultPrice = 180.0,
            defaultCapacity = 12,
            defaultDuration = 120.minutes,
            customFields = listOf(
                BooleanFieldDefinition(
                    key = "own_mat",
                    label = "Přinesu si vlastní podložku (sleva 10 Kč)",
                    isRequired = false
                )
            )
        )

        val defCeramics = EventDefinition(
            id = defCeramicsId,
            title = "Keramická dílna pro děti",
            description = "Tvoření z hlíny pro děti od 6 let. Cena zahrnuje výpal i glazury.",
            defaultPrice = 2200.0,
            defaultCapacity = 8,
            defaultDuration = 120.minutes,
            customFields = listOf(
                TextFieldDefinition(
                    key = "child_name",
                    label = "Jméno a příjmení dítěte",
                    isRequired = true,
                    isMultiline = false
                ),
                NumberFieldDefinition(
                    key = "child_age",
                    label = "Věk dítěte",
                    isRequired = true,
                    min = 6,
                    max = 15
                ),
                TextFieldDefinition(
                    key = "pickup_info",
                    label = "Kdo dítě vyzvedává (pokud ne rodič)",
                    isRequired = false,
                    isMultiline = false
                )
            )
        )

        val defParty = EventDefinition(
            id = defPartyId,
            title = "Soukromý pronájem herny (Oslava)",
            description = "Rezervace celé herny pro dětskou oslavu. K dispozici kuchyňka a audio systém.",
            defaultPrice = 3000.0,
            defaultCapacity = 1,
            defaultDuration = 1.hours,
            customFields = listOf(
                TextFieldDefinition(
                    key = "celebrant_name",
                    label = "Jméno oslavence",
                    isRequired = true
                ),
                NumberFieldDefinition(
                    key = "guest_count",
                    label = "Předpokládaný počet dětí",
                    isRequired = true,
                    min = 1,
                    max = 30
                ),
                TimeRangeFieldDefinition(
                    key = "party_time_range",
                    label = "Preferovaný čas oslavy (od - do)",
                    isRequired = true
                ),
                TextFieldDefinition(
                    key = "catering_note",
                    label = "Poznámka k občerstvení / Alergie",
                    isRequired = false,
                    isMultiline = true
                )
            )
        )

        defRepo.create(defYoga)
        defRepo.create(defCeramics)
        defRepo.create(defParty)


        // ==========================================
        // 2. SÉRIE (Kroužky)
        // ==========================================

        val seriesCeramics = EventSeries(
            id = seriesCeramicsId,
            definitionId = defCeramicsId, // Bezpečně napojeno přes Uuid
            title = "Jarní Keramika (Úterky)",
            capacity = 8,
            occupiedSpots = 6,
            price = 2200.0,
            startDate = today.plus(DatePeriod(days = 7)),
            endDate = today.plus(DatePeriod(days = 7 + (10 * 7))),
            lessonCount = 10,
            description = "Keramika pro začátečníky",
            customFields = listOf(),
        )
        seriesRepo.create(seriesCeramics)


        // ==========================================
        // 3. INSTANCE (Jednorázovky)
        // ==========================================

        instRepo.create(EventInstance(
            id = Uuid.random(),
            definitionId = defYogaId, // Vazba na jógu
            title = defYoga.title,
            startDateTime = now.date.atTime(18, 0),
            endDateTime = now.date.atTime(19, 0),
            price = 180.0,
            capacity = 12,
            occupiedSpots = 4,
            description = defYoga.description
        ))

        instRepo.create(EventInstance(
            id = Uuid.random(),
            definitionId = defYogaId,
            title = "Power Jóga (Intenzivní)",
            startDateTime = now.date.plus(DatePeriod(days = 1)).atTime(18, 0),
            endDateTime = now.date.plus(DatePeriod(days = 1)).atTime(19, 0),
            price = 200.0,
            capacity = 10,
            occupiedSpots = 10,
            description = defYoga.description,
        ))

        instRepo.create(EventInstance(
            id = Uuid.random(),
            definitionId = defPartyId, // Vazba na oslavy
            title = "Víkendový pronájem herny",
            startDateTime = today.plus(DatePeriod(days = 2)).atTime(10, 0),
            endDateTime = today.plus(DatePeriod(days = 2)).atTime(18, 0),
            price = 3000.0,
            capacity = 1,
            occupiedSpots = 0,
            description = defParty.description,
        ))

        instRepo.create(EventInstance(
            id = Uuid.random(),
            definitionId = defYogaId,
            title = "Jóga (Proběhlo)",
            startDateTime = now.date.minus(DatePeriod(days = 5)).atTime(10, 0),
            endDateTime = now.date.minus(DatePeriod(days = 5)).atTime(11, 0),
            price = 150.0,
            capacity = 10,
            occupiedSpots = 5,
            description = defYoga.description
        ))
    }
}