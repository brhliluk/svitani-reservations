package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.service.ConsoleEmailService
import cz.svitaninymburk.projects.reservations.service.EmailService
import cz.svitaninymburk.projects.reservations.service.WaitlistPromoter

/**
 * Povyšování z pořadníku pro testy. Repozitáře musí být ty stejné instance, jaké dostane
 * testovaná služba — jinak promoter uvidí jiný stav než ona.
 */
fun testWaitlistPromoter(
    instanceRepo: EventInstanceRepository = InMemoryEventInstanceRepository(),
    seriesRepo: EventSeriesRepository = InMemoryEventSeriesRepository(),
    reservationRepo: ReservationRepository = InMemoryReservationRepository(),
    emailService: EmailService = ConsoleEmailService(),
) = WaitlistPromoter(
    eventInstanceRepository = instanceRepo,
    eventSeriesRepository = seriesRepo,
    reservationRepository = reservationRepo,
    emailService = emailService,
    qrCodeService = StubQrCodeGenerator(),
    appBaseUrl = "https://test.example.com",
)
