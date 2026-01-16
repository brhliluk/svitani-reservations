package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.bank.FioResponse
import cz.svitaninymburk.projects.reservations.bank.parseFioTransactions
import cz.svitaninymburk.projects.reservations.error.PaymentPairingError
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.server.util.url
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.channels.Channel
import kotlin.reflect.jvm.jvmName


class PaymentPairingService(
    private val httpClient: HttpClient,
    private val reservationRepo: ReservationRepository,
    private val emailService: EmailService,
    private val qrCodeService: QrCodeService,
    private val fioToken: String
) {
    private val logger = KtorSimpleLogger(this::class.jvmName)
    suspend fun checkAndPairPayments(): Either<PaymentPairingError.CheckAndPairPayments, Unit> = either {
            logger.info("🔄 Spouštím kontrolu plateb Fio banky...")

            val response = try {
                httpClient.get(url {
                    protocol = URLProtocol.HTTPS
                    host = "fio.cz"
                    path("ib_api/rest/last/$fioToken/transactions.json")
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
            return
        }

        if ((transaction.amount < reservation.totalPrice) || (transaction.amount != reservation.unpaidAmount)) {
            logger.warn("⚠️ Nedoplatek! VS $vs: Očekávaná čáska: ${reservation.unpaidAmount}, přišlo ${transaction.amount}.")
            emailService.sendPaymentNotPaidInFull(reservation, transaction, fioToken, qrCodeService.generateQrPaymentImage(reservation.copy(totalPrice = reservation.unpaidAmount - transaction.amount)))
            return
        }

        val paidReservation = reservation.copy(
            status = Reservation.Status.CONFIRMED,
            paidAmount = transaction.amount,
            paymentPairingToken = transaction.remoteId,
        )
        reservationRepo.save(paidReservation)

        emailService.sendPaymentReceivedConfirmation(paidReservation)

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
