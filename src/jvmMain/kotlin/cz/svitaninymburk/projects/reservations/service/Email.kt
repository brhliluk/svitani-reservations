package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.right
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.i18n.emailStringsFor
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.a
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
import kotlin.uuid.Uuid


class GmailEmailService(
    private val username: String,
    private val appPassword: String,
    private val appBaseUrl: String,
    private val eventRepository: EventInstanceRepository,
) : EmailService {

    private fun setupEmail() : HtmlEmail {
        val email = HtmlEmail()
        email.hostName = "smtp.gmail.com"
        email.setSslSmtpPort("465")
        email.setAuthenticator(DefaultAuthenticator(username, appPassword))
        email.isSSLOnConnect = true

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

        val s = emailStringsFor(reservation.locale)
        email.addTo(toEmail)
        email.subject = s.reservationConfirmationSubject(reservation.id.toString())

        val dataSource = ByteArrayDataSource(qrCodeImage, "image/png")
        val cid = email.embed(dataSource, "qr-code-platba")

        // TODO: differentiate between services and instances
        val event = eventRepository.get(reservation.reference.id)
        val htmlMessage = buildString { appendHTML().html { body {
            h1 { +s.reservationConfirmationHeading }
            p { +s.reservationConfirmationBody(event?.title ?: reservation.reference.id.toString()) }
            p { +s.reservationPaymentDetails }
            p {
                strong { +s.reservationPrice }
                +"${reservation.totalPrice} Kč"
            }
            p { +s.reservationPaymentQrPrompt }
            img {
                src = "cid:$cid"
                alt = s.reservationQrAlt
                width = "200"
                height = "200"
            }
            br
            p { +s.reservationBankTransfer(bankAccount, reservation.variableSymbol) }
        } } }

        email.setHtmlMsg(htmlMessage)
        email.setTextMsg(s.reservationHtmlFallback)

        catch({ email.send() }) { e: EmailException ->
            raise(EmailError.SendReservationConfirmationFailed(e.message ?: "Unknown error"))
        }
    } }

    override suspend fun sendCancellationNotice(toEmail: String, reservationId: Uuid): Either<EmailError.SendCancellation, Unit> = either { withContext(Dispatchers.IO) {
        val s = emailStringsFor("cs") // TODO: pass locale when interface supports it
        val email = setupEmail()
        email.addTo(toEmail)
        val event = eventRepository.get(reservationId)

        email.subject = s.cancellationSubject(event?.title)
        email.setTextMsg(s.cancellationBody(event?.title))

        catch({ email.send() }) { e: EmailException ->
            raise(EmailError.SendCancellationFailed(e.message ?: "Unknown error"))
        }
    } }

    override suspend fun sendPaymentReceivedConfirmation(reservation: Reservation): Either<EmailError.SendPaymentConfirmation, Unit> = either { withContext(Dispatchers.IO) {
        val s = emailStringsFor(reservation.locale)
        val email = setupEmail()
        email.addTo(reservation.contactEmail)
        email.subject = s.paymentReceivedSubject

        val event = eventRepository.get(reservation.reference.id)

        email.setTextMsg(s.paymentReceivedBody(event?.title))

        catch({ email.send() }) { e: EmailException ->
            raise(EmailError.SendPaymentConfirmationFailed(e.message ?: "Unknown error"))
        }
    } }

    override suspend fun sendPaymentNotPaidInFull(
        reservation: Reservation,
        paymentInfo: BankTransaction,
        bankAccount: String,
        qrCodeImage: String,
    ): Either<EmailError.SendPaymentNotPaidInFull, Unit> = either { withContext(Dispatchers.IO) {
        val s = emailStringsFor(reservation.locale)
        val email = setupEmail()
        email.addTo(reservation.contactEmail)
        email.subject = s.partialPaymentSubject

        val event = eventRepository.get(reservation.reference.id)

        // TODO: reflect the change from byteArray to svg string
        val dataSource = ByteArrayDataSource(qrCodeImage, "image/png")
        val cid = email.embed(dataSource, "qr-code-platba")

        email.setHtmlMsg(buildString { appendHTML().html { body {
            p { +s.partialPaymentBody(event?.title) }
            p { +s.partialPaymentAmount(paymentInfo.amount) }
            p { +s.partialPaymentRemaining(reservation.totalPrice - paymentInfo.amount) }
            p { +s.partialPaymentDetails }
            img {
                src = "cid:$cid"
                alt = s.reservationQrAlt
                width = "200"
                height = "200"
            }
            br
            p { +s.reservationBankTransfer(bankAccount, reservation.variableSymbol) }
        } } })

        catch({ email.send() }) { e: EmailException ->
            raise(EmailError.SendPaymentNotPaidInFullFailed(e.message ?: "Unknown error"))
        }
    } }

    override suspend fun sendPasswordResetEmail(toEmail: String, resetToken: String): Either<EmailError.SendPasswordReset, Unit> = either {
        val s = emailStringsFor("cs") // TODO: pass locale when interface supports it
        val email = setupEmail()
        email.addTo(toEmail)
        email.subject = s.passwordResetSubject

        email.setHtmlMsg(buildString { appendHTML().html { body {
            h1 { +s.passwordResetHeading }

            p { +s.passwordResetBody }
            a {
                +s.passwordResetLinkText
                href = "$appBaseUrl/reset-password/$resetToken"
            }
        } } })

        catch({ email.send() }) { e: EmailException ->
            raise(EmailError.SendPasswordResetFailed(e.message ?: "Unknown error"))
        }
    }
}

class ConsoleEmailService : EmailService {
    override suspend fun sendReservationConfirmation(
        toEmail: String,
        reservation: Reservation,
        bankAccount: String,
        qrCodeImage: ByteArray
    ): Either<EmailError.SendReservationConfirmation, Unit> {
        println("📧 [MOCK EMAIL] Odesílám potvrzení rezervace na: $toEmail")
        println("   ID: ${reservation.id}, Cena: ${reservation.totalPrice}")
        return Unit.right()
    }

    override suspend fun sendCancellationNotice(
        toEmail: String,
        reservationId: Uuid
    ): Either<EmailError.SendCancellation, Unit> {
        println("📧 [MOCK EMAIL] Odesílám storno na: $toEmail (ID: $reservationId)")
        return Unit.right()
    }

    override suspend fun sendPaymentReceivedConfirmation(
        reservation: Reservation
    ): Either<EmailError.SendPaymentConfirmation, Unit> {
        println("📧 [MOCK EMAIL] Potvrzení platby pro: ${reservation.contactEmail}")
        return Unit.right()
    }

    override suspend fun sendPaymentNotPaidInFull(
        reservation: Reservation,
        paymentInfo: BankTransaction,
        bankAccount: String,
        qrCodeImage: String
    ): Either<EmailError.SendPaymentNotPaidInFull, Unit> {
        println("📧 [MOCK EMAIL] Nedoplatek pro: ${reservation.contactEmail}")
        return Unit.right()
    }

    override suspend fun sendPasswordResetEmail(
        toEmail: String,
        resetToken: String
    ): Either<EmailError.SendPasswordReset, Unit> {
        println("📧 [MOCK EMAIL] Odesílám reset hesla na: $toEmail")
        return Unit.right()
    }
}