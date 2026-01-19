package cz.svitaninymburk.projects.reservations.debug

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.event.RecurrenceType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

val randomDefinitionList: List<EventDefinition> get() = listOf(
    // ID 0 -> Pro tvou instanci "Herna"
    EventDefinition(
        id = "0",
        title = "Volná herna",
        description = "Přijďte si pohrát do naší herny. Otevřeno pro všechny věkové kategorie.",
        defaultPrice = 0.0,
        defaultCapacity = 30,
        defaultDuration = 2.hours,
        recurrenceType = RecurrenceType.NONE
    ),
    // ID 1 -> Pro tvou instanci "Smyslohrátky"
    EventDefinition(
        id = "1",
        title = "Smyslohrátky",
        description = "Senzorické hraní pro nejmenší. Patlání, zkoumání a objevování všemi smysly.",
        defaultPrice = 100.0,
        defaultCapacity = 15,
        defaultDuration = 90.minutes,
        recurrenceType = RecurrenceType.NONE
    ),
    // ID 2 -> Nová definice pro Kroužek
    EventDefinition(
        id = "2",
        title = "Výtvarný kroužek",
        description = "Pravidelné tvoření pro děti od 5 let. Kresba, malba, keramika.",
        defaultPrice = 200.0, // Cena za jednu lekci (kdyby se šlo jednorázově)
        defaultCapacity = 10,
        defaultDuration = 60.minutes,
        recurrenceType = RecurrenceType.WEEKLY
    )
)

// --- MOCK SÉRIE (Kurzy / Kroužky) ---
val randomSeriesList: List<EventSeries> get() = listOf(
    EventSeries(
        id = "series_1",
        definitionId = "2", // Odkaz na "Výtvarný kroužek"
        title = "Výtvarka - Podzim 2026",
        description = "Celosemestrální kurz zaměřený na rozvoj kreativity. Každé úterý od 15:00.",
        price = 2500.0, // Zvýhodněná cena za celý kroužek
        capacity = 10,
        startDate = LocalDate(2026, 9, 1),
        endDate = LocalDate(2027, 1, 31),
        lessonCount = 15
    ),
    EventSeries(
        id = "series_2",
        definitionId = "0", // Třeba "Herna" jako permanentka/série? Proč ne.
        title = "Předplatné Herna - Zima",
        description = "Neomezený vstup do herny na zimní měsíce.",
        price = 1500.0,
        capacity = 50,
        startDate = LocalDate(2026, 12, 1),
        endDate = LocalDate(2027, 2, 28),
        lessonCount = 90 // Každý den
    )
)

// --- MOCK INSTANCE (Tvoje + vygenerované lekce kroužku) ---
val randomEventList: List<EventInstance> get() {
    // 1. Tvoje původní jednorázovky
    val standaloneEvents = listOf(
        EventInstance(
            "0", "1", null, // Smyslohrátky
            "Smyslohrátky", "Druhé smyslohrátky letošní",
            LocalDateTime(2026, 12, 12, 10, 0),
            LocalDateTime(2026, 12, 12, 11, 30),
            100.0, 30
        ),
        EventInstance(
            "1", "0", null, // Herna
            "Herna", "Letsgooo",
            LocalDateTime(2026, 12, 24, 12, 12),
            LocalDateTime(2026, 12, 24, 14, 12),
            0.0, 30
        ),
    )

    // 2. Vygenerované lekce pro "Výtvarku" (aby bylo vidět propojení se sérií)
    val courseLessons = listOf(
        EventInstance(
            id = "lesson_1", definitionId = "2", seriesId = "series_1", // Vazba na sérii!
            title = "Výtvarka - 1. lekce", description = "Úvod do kresby",
            startDateTime = LocalDateTime(2026, 9, 8, 15, 0),
            endDateTime = LocalDateTime(2026, 9, 8, 16, 0),
            price = 200.0, capacity = 10, occupiedSpots = 5
        ),
        EventInstance(
            id = "lesson_2", definitionId = "2", seriesId = "series_1",
            title = "Výtvarka - 2. lekce", description = "Práce s uhlem",
            startDateTime = LocalDateTime(2026, 9, 15, 15, 0),
            endDateTime = LocalDateTime(2026, 9, 15, 16, 0),
            price = 200.0, capacity = 10, occupiedSpots = 5
        )
    )

    return standaloneEvents + courseLessons
}