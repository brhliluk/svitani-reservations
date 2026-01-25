package cz.svitaninymburk.projects.reservations.debug

import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.reservation.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

val now = Clock.System.now()

// --- MOCK REZERVACE ---

val mockReservations = listOf(
    // 1. SCÉNÁŘ: Jóga (Jednorázová, QR kód, Čeká na platbu)
    Reservation(
        id = "RES-2024-001",
        reference = Reference.Instance("evt-yoga-morning"), // Musí sedět s mockEvents
        userId = null,

        // Kontaktní údaje
        contactName = "Jana Nováková",
        contactEmail = "jana.novakova@email.cz",
        contactPhone = "+420 777 111 222",

        // Detaily
        seatCount = 1,
        totalPrice = 150.0,
        variableSymbol = "2024001",

        paymentType = PaymentInfo.Type.BANK_TRANSFER,

        status = Reservation.Status.PENDING_PAYMENT,
        customValues = listOf(
            BooleanValue(fieldKey = "own_mat", value = true)
        ),

        createdAt = now,
    ),

    // 2. SCÉNÁŘ: Keramika pro děti (Série, Převodem, ZAPLACENO)
    Reservation(
        id = "RES-2024-002",
        reference = Reference.Series("ser-ceramics-kids"), // Musí sedět s mockSeries
        userId = "user-123", // Registrovaný uživatel

        contactName = "Petr Svoboda",
        contactEmail = "petr.svoboda@email.cz",
        contactPhone = "+420 608 123 456",

        seatCount = 1,
        totalPrice = 2500.0, // Cena za celý kurz
        variableSymbol = "2024002",

        status = Reservation.Status.CONFIRMED,
        paymentType = PaymentInfo.Type.BANK_TRANSFER,

        // Custom Fields (Jméno dítěte, Věk)
        customValues = listOf(
            TextValue(fieldKey = "child_name", value = "Adámek Svoboda"),
            NumberValue(fieldKey = "child_age", value = 8f),
            TextValue(fieldKey = "allergies", value = "Žádné")
        ),

        createdAt = now,
    ),

    // 3. SCÉNÁŘ: Workshop Vaření (Skupina, Hotově, Specifický čas)
    Reservation(
        id = "RES-2024-003",
        reference = Reference.Instance("evt-cooking-italian"),
        userId = null,

        contactName = "Rodina Dvořákova",
        contactEmail = "rodina@dvorakovi.cz",
        contactPhone = "+420 777 999 888",

        seatCount = 4, // Táta, Máma, 2 děti
        totalPrice = 4000.0,
        variableSymbol = "2024003",

        status = Reservation.Status.CONFIRMED, // Hotově na místě = bráno jako potvrzené (nebo CREATED podle logiky)
        paymentType = PaymentInfo.Type.ON_SITE,

        customValues = listOf(
            // TimeRange: Preferovaný čas ochutnávky
            TimeRangeValue(
                fieldKey = "tasting_time",
                from = now.toLocalDateTime(TimeZone.currentSystemDefault()).time, // Tady by reálně byl konkrétní čas akce
                to = now.plus(1.hours).toLocalDateTime(TimeZone.currentSystemDefault()).time
            ),
            BooleanValue(fieldKey = "vegetarian", value = false)
        ),

        createdAt = now,
    ),

    // 4. SCÉNÁŘ: Stornovaná rezervace
    Reservation(
        id = "RES-2024-004",
        reference = Reference.Instance("evt-yoga-evening"),
        userId = null,
        contactName = "Karel Zrušil",
        contactEmail = "karel@zrusil.cz",
        contactPhone = "+420 111 222 333",
        seatCount = 1,
        totalPrice = 150.0,
        variableSymbol = "2024004",
        status = Reservation.Status.CANCELLED,
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
        customValues = emptyList(),
        createdAt = now,
    )
)