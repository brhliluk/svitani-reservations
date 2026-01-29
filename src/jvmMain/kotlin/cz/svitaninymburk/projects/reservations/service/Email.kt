package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.h1
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.stream.appendHTML
import kotlinx.html.strong
import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.EmailException
import org.apache.commons.mail.HtmlEmail
import javax.mail.util.ByteArrayDataSource


class GmailEmailService(
    private val username: String,
    private val appPassword: String,
    private val eventRepository: EventInstanceRepository,
) : EmailService {

    private fun setupEmail() : HtmlEmail {
        val email = HtmlEmail()
        email.hostName = "smtp.gmail.com"
        email.setSmtpPort(587)
        email.setAuthenticator(DefaultAuthenticator(username, appPassword))
        email.isStartTLSEnabled = true

        email.setFrom(username, "Rodinné centrum Svítání")
        return email
    }

    // TODO: ical
    override suspend fun sendReservationConfirmation(
        toEmail: String,
        reservation: Reservation,
        bankAccount: String,
        qrCodeImage: ByteArray,
    ): Either<EmailError.SendReservationConfirmation, Unit> = either { withContext(Dispatchers.IO) {
        val email = setupEmail()
        email.setAuthenticator(DefaultAuthenticator(username, appPassword))

        email.addTo(toEmail)
        email.subject = "Potvrzení rezervace: ${reservation.id}"

        val dataSource = ByteArrayDataSource(qrCodeImage, "image/png")
        val cid = email.embed(dataSource, "qr-code-platba")

        // TODO: differentiate between services and instances
        val event = eventRepository.get(reservation.reference.id)
        val htmlMessage = buildString { appendHTML().html { body {
            h1 { +"Děkujeme za rezervaci!" }
            p { +"Vaše místa na akci: ${event?.title ?: reservation.reference.id} jsou zarezervována." }
            p { +"Pokud jste ještě neplatili, platební údaje:" }
            p {
                strong { +"Cena:" }
                +"${reservation.totalPrice} Kč"
            }
            p { +"Pro dokončení prosím uhraďte částku pomocí QR kódu níže:" }
            img {
                src = "cid:$cid"
                alt = "QR Platba"
                width = "200"
                height = "200"
            }
            br
            p { +"Nebo převodem na účet: $bankAccount, VS: ${reservation.variableSymbol}" }
        } } }

        email.setHtmlMsg(htmlMessage)
        email.setTextMsg("Váš klient nepodporuje HTML emaily.")

        catch({ email.send() }) { e: EmailException ->
            EmailError.SendReservationConfirmationFailed(e.message ?: "Unknown error")
        }
    } }

    override suspend fun sendCancellationNotice(toEmail: String, reservationId: String): Either<EmailError.SendCancellation, Unit> = either { withContext(Dispatchers.IO) {
        val email = setupEmail()
        email.addTo(toEmail)
        val event = eventRepository.get(reservationId)

        email.subject = "Zrušení rezervace: ${event?.title}"
        email.setTextMsg("Vaše rezervace na akci: ${event?.title} byla zrušena.")

        catch({ email.send() }) { e: EmailException ->
            EmailError.SendCancellationFailed(e.message ?: "Unknown error")
        }
    } }

    override suspend fun sendPaymentReceivedConfirmation(reservation: Reservation): Either<EmailError.SendReservationConfirmation, Unit> = either { withContext(Dispatchers.IO) {
        val email = setupEmail()
        email.addTo(reservation.contactEmail)
        email.subject = "Potvrzení platby"

        val event = eventRepository.get(reservation.reference.id)

        email.setTextMsg("Vaše rezervace na akci: ${event?.title} byla zaplacena, děkujeme!")

        catch({ email.send() }) { e: EmailException ->
            EmailError.SendPaymentConfirmationFailed(e.message ?: "Unknown error")
        }
    } }

    override suspend fun sendPaymentNotPaidInFull(
        reservation: Reservation,
        paymentInfo: BankTransaction,
        bankAccount: String,
        qrCodeImage: String,
    ): Either<EmailError.SendReservationConfirmation, Unit> = either { withContext(Dispatchers.IO) {
        val email = setupEmail()
        email.addTo(reservation.contactEmail)
        email.subject = "Částečně zaplaceno"

        val event = eventRepository.get(reservation.reference.id)

        // TODO: reflect the change from byteArray to svg string
        val dataSource = ByteArrayDataSource(qrCodeImage, "image/png")
        val cid = email.embed(dataSource, "qr-code-platba")

        email.setHtmlMsg(buildString { appendHTML().html { body {
            p { +"Vaše rezervace na akci: ${event?.title} byla zaplacena pouze částečně!" }
            p { +"Zaznamenali jsme platbu v hodnotě: ${paymentInfo.amount}" }
            p { +"Zbývá doplatit: ${reservation.totalPrice - paymentInfo.amount}" }
            p { +"Platební údaje k doplacení platby: "}
            img {
                src = "cid:$cid"
                alt = "QR Platba"
                width = "200"
                height = "200"
            }
            br
            p { +"Nebo převodem na účet: $bankAccount, VS: ${reservation.variableSymbol}" }
        } } })

        catch({ email.send() }) { e: EmailException ->
            EmailError.SendPaymentNotPaidInFullFailed(e.message ?: "Unknown error")
        }
    } }


}