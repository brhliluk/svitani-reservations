package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.util.captureEmailError
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.reflect.jvm.jvmName
import kotlin.time.Clock

/**
 * Povyšování z pořadníku na volná místa. Sdílený kolaborant, protože místo se uvolní
 * ze dvou různých směrů: zrušením/omluvenkou (viz [ReservationService]) a zvednutím
 * kapacity v administraci (viz [AdminDashboardService]).
 */
class WaitlistPromoter(
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val reservationRepository: ReservationRepository,
    private val emailService: EmailService,
    private val qrCodeService: QrCodeGeneratorService,
    private val appBaseUrl: String,
) {

    private val logger = KtorSimpleLogger(this::class.jvmName)

    /**
     * Zvednutí kapacity žádné místo neuvolní explicitně, takže rozpočtem je rovnou
     * počet volných míst po uložení. U lekcí v kurzu čte repository obsazenost už se
     * zátěží kurzu (`SeriesAwareEventInstanceRepository`), takže `capacity - occupiedSpots`
     * je skutečný stav; strop stejně drží atomický `attemptToReserveSpots` v [promote].
     */
    suspend fun promoteAfterCapacityIncrease(reference: Reference) {
        val freeSeats = when (reference) {
            is Reference.Instance -> {
                val instance = eventInstanceRepository.get(reference.id) ?: return
                if (instance.isCancelled) return
                // Zvednutí kapacity u proběhlé lekce nemá rozesílat pozvánky.
                val timezone = TimeZone.of("Europe/Prague")
                if (instance.startDateTime.toInstant(timezone) < Clock.System.now()) return
                instance.capacity - instance.occupiedSpots
            }

            is Reference.Series -> {
                val series = eventSeriesRepository.get(reference.id) ?: return
                if (series.isCancelled) return
                series.capacity - series.occupiedSpots
            }
        }

        if (freeSeats <= 0) return

        logger.info("Capacity increase on $reference freed $freeSeats seat(s), checking waitlist")
        promote(reference, freedSeats = freeSeats)
    }

    suspend fun promote(reference: Reference, freedSeats: Int) {
        val waitlisted = reservationRepository.findByReference(reference)
            .filter { it.status == Reservation.Status.WAITLISTED }
            .sortedBy { it.createdAt }

        var slotsLeft = freedSeats
        for (candidate in waitlisted) {
            if (slotsLeft <= 0) break

            val acquired = when (reference) {
                is Reference.Instance -> eventInstanceRepository.attemptToReserveSpots(reference.id, candidate.seatCount)
                is Reference.Series -> eventSeriesRepository.attemptToReserveSpots(reference.id, candidate.seatCount)
            }
            if (!acquired) break

            // O jednu přihlášku, ne o počet míst: zápis do pořadníku ho zvedá
            // o 1 (attemptToReserveWaitlistSpot) a strop se kontroluje stejně,
            // takže odečítat po místech by čítač táhlo do záporu.
            when (reference) {
                is Reference.Instance -> eventInstanceRepository.decrementOccupiedWaitlist(reference.id, 1)
                is Reference.Series -> eventSeriesRepository.decrementOccupiedWaitlist(reference.id, 1)
            }

            val variableSymbol = reservationRepository.generateUniqueVariableSymbol()
            // Povýšení z pořadníku u akce zdarma nesmí skončit ve "čeká na platbu" —
            // viz stejné rozhodnutí v createReservationFlow.
            val promoted = candidate.copy(
                status = if (candidate.isFree) Reservation.Status.CONFIRMED else Reservation.Status.PENDING_PAYMENT,
                paymentType = if (candidate.isFree) PaymentInfo.Type.FREE else candidate.paymentType,
                variableSymbol = variableSymbol,
            )
            reservationRepository.save(promoted)

            val target: ReservationTarget? = when (reference) {
                is Reference.Instance -> eventInstanceRepository.get(reference.id)?.let { ReservationTarget.Instance(it) }
                is Reference.Series -> eventSeriesRepository.get(reference.id)?.let { ReservationTarget.Series(it.copy(lessonCount = eventInstanceRepository.countActiveBySeries(it.id).toInt())) }
            }

            if (target != null) {
                val qrImage: ByteArray? = if (promoted.paymentType == PaymentInfo.Type.BANK_TRANSFER) {
                    qrCodeService.generateQrPng(promoted)
                } else null

                val icalBytes = when (target) {
                    is ReservationTarget.Instance -> ICalGenerator.forInstance(target.event, promoted.id, appBaseUrl)
                    is ReservationTarget.Series -> ICalGenerator.forSeries(target.series, promoted.id, appBaseUrl)
                }.toByteArray(Charsets.UTF_8)

                emailService.sendWaitlistPromotion(
                    toEmail = promoted.contactEmail,
                    reservation = promoted,
                    target = target,
                    bankAccount = qrCodeService.accountNumber,
                    qrCodeImage = qrImage,
                    icalBytes = icalBytes,
                ).onLeft { captureEmailError(logger, "Failed to send waitlist promotion email for reservation ${promoted.id}: $it") }
            }

            slotsLeft -= candidate.seatCount
        }
    }
}
