package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.bank.FioResponse
import cz.svitaninymburk.projects.reservations.bank.parseFioTransactions
import cz.svitaninymburk.projects.reservations.error.PaymentPairingError
import cz.svitaninymburk.projects.reservations.service.QrCodeGeneratorService
import cz.svitaninymburk.projects.reservations.repository.payment.NewPaymentEvent
import cz.svitaninymburk.projects.reservations.repository.payment.PaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.server.util.url
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.util.AuditSubject
import cz.svitaninymburk.projects.reservations.util.withAuditSubject
import io.ktor.util.logging.KtorSimpleLogger
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.channels.Channel
import kotlin.reflect.jvm.jvmName


class PaymentPairingService(
    private val httpClient: HttpClient,
    private val reservationRepo: ReservationRepository,
    private val emailService: EmailService,
    private val qrCodeService: QrCodeGeneratorService,
    private val settings: AppSettingsProvider,
    private val paymentEventRepository: PaymentEventRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val audit: AuditService = AuditService(InMemoryAuditRepository()),
    /** Dorovnání kreditu za omluvenky z doby před zaplacením; null jen v testech, které ho nepotřebují. */
    private val lessonOptOutRefunds: LessonOptOutRefunds? = null,
) {
    private val logger = KtorSimpleLogger(this::class.jvmName)

    /**
     * Po každém zvýšení zaplacené částky, i u nedoplatku — omluvenky mají nárok
     * až do výše toho, co opravdu přišlo. Selhání vratky nesmí shodit párování:
     * platba je už uložená a zbylé transakce z dávky by se nespárovaly.
     */
    private suspend fun settleLessonOptOutRefunds(reservation: Reservation) {
        val refunds = lessonOptOutRefunds ?: return
        runCatching { refunds.settleAfterPayment(reservation) }.onFailure { e ->
            logger.error("Failed to settle lesson opt-out refunds for reservation ${reservation.id}", e)
            Sentry.captureException(e)
        }
    }

    /**
     * Ke které akci platba patří. U rezervace na jednotlivou lekci dohledá i kurz,
     * ať se záznam objeví na obou detailech.
     */
    private suspend fun subjectFor(reservation: Reservation): AuditSubject = when (val ref = reservation.reference) {
        is Reference.Instance -> AuditSubject(
            seriesId = eventInstanceRepository.get(ref.id)?.seriesId,
            instanceId = ref.id,
            reservationId = reservation.id,
            label = reservation.contactName,
        )
        is Reference.Series -> AuditSubject(
            seriesId = ref.id,
            reservationId = reservation.id,
            label = reservation.contactName,
        )
    }

    /** Akce nebo kurz rezervace — QR kód z ní bere název do zprávy pro příjemce. */
    private suspend fun targetFor(reservation: Reservation): ReservationTarget? = when (val ref = reservation.reference) {
        is Reference.Instance -> eventInstanceRepository.get(ref.id)?.let { ReservationTarget.Instance(it) }
        is Reference.Series -> eventSeriesRepository.get(ref.id)?.let { ReservationTarget.Series(it) }
    }
    suspend fun checkAndPairPayments(): Either<PaymentPairingError.CheckAndPairPayments, Unit> = either {
        logger.info("🔄 Spouštím kontrolu plateb Fio banky...")

        val response = try {
            httpClient.get(url {
                protocol = URLProtocol.HTTPS
                host = "fioapi.fio.cz"
                path("v1/rest/last/${settings.current.fioToken}/transactions.json")
            })
        } catch (e: Exception) {
            raise(PaymentPairingError.Upstream(e, e.message ?: "Unknown error"))
        }

        ensure(response.status.isSuccess()) { PaymentPairingError.Failed(response.status.toString())  }

        val transactions = parseFioTransactions(response.body<FioResponse>())

        logger.info("📥 Staženo ${transactions.size} nových transakcí.")

        transactions.forEach { processTransaction(it) }
    }

    private suspend fun processTransaction(transaction: BankTransaction) {
        val vs = transaction.variableSymbol
        if (vs.isNullOrBlank()) {
            logger.debug("⚠️ Transakce ${transaction.remoteId} nemá VS, nelze spárovat.")
            return
        }

        val reservation = reservationRepo.findAwaitingPayment(vs) ?: run {
            logger.warn("❓ Platba s VS $vs nenašla žádnou čekající rezervaci.")
            // Bez vazby na akci, zato dohledatelné — tohle je přesně ten případ,
            // který dřív zmizel v logu a musel se řešit ručně z výpisu z banky.
            audit.record(
                type = AuditEventType.PAYMENT_UNMATCHED,
                subjectLabel = "VS $vs",
                amount = transaction.amount,
                detail = "Platba ${transaction.amount} ${transaction.currency} (banka id ${transaction.remoteId}) neodpovídá žádné čekající rezervaci",
            )
            return
        }

        if (transaction.amount < reservation.unpaidAmount) {
            logger.warn("⚠️ Nedoplatek! VS $vs: Očekávaná částka: ${reservation.unpaidAmount}, přišlo ${transaction.amount}.")
            Sentry.withScope { scope ->
                scope.setTag("vs", vs)
                scope.setTag("reservation_id", reservation.id.toString())
                scope.setExtra("expected_amount", reservation.unpaidAmount.toString())
                scope.setExtra("received_amount", transaction.amount.toString())
                Sentry.captureMessage("Nedoplatek: VS $vs", SentryLevel.WARNING)
            }
            val updatedReservation = reservation.copy(
                paidAmount = reservation.paidAmount + transaction.amount
            )
            reservationRepo.save(updatedReservation)

            runCatching {
                paymentEventRepository.insert(
                    NewPaymentEvent(
                        reservationId = reservation.id,
                        amount = transaction.amount,
                        type = PaymentType.BANK_TRANSFER,
                        source = PaymentEvent.Source.AUTO_FIO,
                    )
                )
            }.onFailure { e ->
                println("WARNING: Failed to record partial payment event for FIO transaction ${transaction.remoteId}: ${e.message}")
            }

            withAuditSubject(subjectFor(updatedReservation)) {
                audit.record(
                    type = AuditEventType.PAYMENT_PARTIAL,
                    subjectLabel = updatedReservation.contactName,
                    amount = transaction.amount,
                    detail = "Nedoplatek: očekáváno ${reservation.unpaidAmount}, přišlo ${transaction.amount}",
                )
                settleLessonOptOutRefunds(updatedReservation)
            }

            emailService.sendPaymentNotPaidInFull(
                updatedReservation,
                transaction,
                settings.current.bankAccountNumber,
                qrCodeService.generateQrPng(updatedReservation, targetFor(updatedReservation)),
            )
            return
        }

        val paidReservation = reservation.copy(
            status = Reservation.Status.CONFIRMED,
            paidAmount = reservation.paidAmount + transaction.amount,
            paymentPairingToken = transaction.remoteId,
        )
        reservationRepo.save(paidReservation)

        runCatching {
            paymentEventRepository.insert(
                NewPaymentEvent(
                    reservationId = reservation.id,
                    amount = transaction.amount,
                    type = PaymentType.BANK_TRANSFER,
                    source = PaymentEvent.Source.AUTO_FIO,
                )
            )
        }.onFailure { e ->
            println("WARNING: Failed to record payment event for FIO transaction ${transaction.remoteId}: ${e.message}")
        }

        val paidSubject = subjectFor(paidReservation)
        audit.record(
            type = AuditEventType.PAYMENT_PAIRED_AUTO,
            subjectLabel = paidReservation.contactName,
            seriesId = paidSubject.seriesId,
            instanceId = paidSubject.instanceId,
            reservationId = paidReservation.id,
            amount = transaction.amount,
            detail = "Spárováno z FIO na VS $vs",
        )

        withAuditSubject(paidSubject) {
            settleLessonOptOutRefunds(paidReservation)

            emailService.sendPaymentReceivedConfirmation(paidReservation)
                .onLeft { error ->
                    Sentry.withScope { scope ->
                        scope.setTag("reservation_id", paidReservation.id.toString())
                        scope.setTag("vs", vs)
                        logger.error("⚠️ Failed to send payment-received email for reservation ${paidReservation.id} (VS $vs): $error")
                    }
                }
        }

        logger.info("✅ Rezervace ${reservation.id} (VS $vs) úspěšně ZAPLACENA.")
    }
}

class PaymentTrigger {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    fun notifyNewReservation() {
        channel.trySend(Unit)
    }

    suspend fun waitForSignal() {
        channel.receive()
    }
}
