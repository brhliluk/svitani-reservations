package cz.svitaninymburk.projects.reservations.mock

import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object MockDataLoader {

    private val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    private val today = now.date

    suspend fun load(
        defRepo: EventDefinitionRepository,
        instRepo: EventInstanceRepository,
        seriesRepo: EventSeriesRepository
    ) {
        // ==========================================
        // 1. DEFINICE (Šablony s Custom Fields)
        // ==========================================

        // A) CVIČENÍ: Jóga (Jednoduchý formulář)
        val defYoga = EventDefinition(
            id = "def-yoga",
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

        // B) KROUŽEK: Keramika pro děti (Formulář pro rodiče)
        val defCeramics = EventDefinition(
            id = "def-ceramics",
            title = "Keramická dílna pro děti",
            description = "Tvoření z hlíny pro děti od 6 let. Cena zahrnuje výpal i glazury.",
            defaultPrice = 2200.0, // Cena za semestr
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

        // C) PRONÁJEM: Oslava narozenin (Složitější formulář s TimeRange)
        val defParty = EventDefinition(
            id = "def-birthday",
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
                // Tady využijeme TimeRange - uživatel si vybere konkrétní čas v rámci bloku
                TimeRangeFieldDefinition(
                    key = "party_time_range",
                    label = "Preferovaný čas oslavy (od - do)",
                    isRequired = true
                ),
                TextFieldDefinition(
                    key = "catering_note",
                    label = "Poznámka k občerstvení / Alergie",
                    isRequired = false,
                    isMultiline = true // Víceřádkový text
                )
            )
        )

        // Uložení definic
        defRepo.create(defYoga)
        defRepo.create(defCeramics)
        defRepo.create(defParty)


        // ==========================================
        // 2. SÉRIE (Kroužky)
        // ==========================================

        val seriesCeramics = EventSeries(
            id = "ser-ceramics-spring",
            definitionId = defCeramics.id, // Vazba na definici Keramiky
            title = "Jarní Keramika (Úterky)",
            capacity = 8,
            occupiedSpots = 6, // Zbývají 2 místa
            price = 2200.0,
            startDate = today.plus(DatePeriod(days = 7)),
            endDate = today.plus(DatePeriod(days = 7 + (10 * 7))), // 10 týdnů
            lessonCount = 10,
            description = "Keramika pro začátečníky",
            customFields = listOf(),
        )
        // Pozor: Musíš mít implementovanou metodu create v InMemoryEventSeriesRepository
        seriesRepo.create(seriesCeramics)


        // ==========================================
        // 3. INSTANCE (Jednorázovky)
        // ==========================================

        // 1. Jóga DNES (Volno)
        instRepo.create(EventInstance(
            id = "evt-yoga-today",
            definitionId = defYoga.id,
            title = defYoga.title, // Dědí název
            startDateTime = now.date.atTime(18, 0),
            endDateTime = now.date.atTime(19, 0),
            price = 180.0,
            capacity = 12,
            occupiedSpots = 4,
            description = defYoga.description
        ))

        // 2. Jóga ZÍTRA (Plno - testování statusu VYPRODÁNO)
        instRepo.create(EventInstance(
            id = "evt-yoga-tomorrow",
            definitionId = defYoga.id,
            title = "Power Jóga (Intenzivní)", // Override názvu
            startDateTime = now.date.plus(DatePeriod(days = 1)).atTime(18, 0),
            endDateTime = now.date.plus(DatePeriod(days = 1)).atTime(19, 0),
            price = 200.0,
            capacity = 10,
            occupiedSpots = 10,
            description = defYoga.description,
        ))

        // 3. Oslava o Víkendu (Testování TimeRange a Multiline textu)
        instRepo.create(EventInstance(
            id = "evt-party-saturday",
            definitionId = defParty.id,
            title = "Víkendový pronájem herny",
            // Celý slot je od 10:00 do 18:00, uživatel si v TimeRange vybere třeba 14:00-16:00
            startDateTime = today.plus(DatePeriod(days = 2)).atTime(10, 0),
            endDateTime = today.plus(DatePeriod(days = 2)).atTime(18, 0),
            price = 3000.0,
            capacity = 1, // Jedna herna
            occupiedSpots = 0,
            description = defParty.description,
        ))

        // 4. Minulá akce (Pro test filtrování)
        instRepo.create(EventInstance(
            id = "evt-past",
            definitionId = defYoga.id,
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