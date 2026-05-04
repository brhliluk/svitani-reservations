package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.right
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.i18n.emailStringsFor
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.util.humanReadable
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

    private fun EmailException.fullMessage(): String = buildString {
        var t: Throwable? = this@fullMessage
        while (t != null) {
            if (isNotEmpty()) append(" → ")
            append(t::class.simpleName).append(": ").append(t.message)
            t = t.cause
        }
    }

    private fun setupEmail() : HtmlEmail {
        val email = HtmlEmail()
        email.hostName = "smtp.gmail.com"
        email.setSslSmtpPort("465")
        email.setAuthenticator(DefaultAuthenticator(username, appPassword))
        email.isSSLOnConnect = true
        email.setCharset("UTF-8")

        email.setFrom(username, "Rodinné centrum Svítání")
        return email
    }

    override suspend fun sendReservationConfirmation(
        toEmail: String,
        reservation: Reservation,
        target: ReservationTarget,
        bankAccount: String,
        qrCodeImage: ByteArray?,
        icalBytes: ByteArray,
    ): Either<EmailError.SendReservationConfirmation, Unit> = either { withContext(Dispatchers.IO) {
        val email = setupEmail()
        val s = emailStringsFor(reservation.locale)

        email.addTo(toEmail)

        val eventDate = target.startDateTime.humanReadable
        email.subject = s.reservationConfirmationSubject(target.title, eventDate)

        // Embed QR only if present (bank transfer payment)
        val cid: String? = if (qrCodeImage != null) {
            val dataSource = ByteArrayDataSource(qrCodeImage, "image/png")
            email.embed(dataSource, "qr-code-platba")
        } else null

        // Attach iCal so Gmail offers "Add to Calendar"
        val icalSource = ByteArrayDataSource(icalBytes, "text/calendar; charset=UTF-8; method=REQUEST")
        email.attach(icalSource, "rezervace.ics", "Rezervace do kalendáře")

        val htmlMessage = buildString { appendHTML().html { body {
            h1 { +s.reservationConfirmationHeading }
            p { +s.reservationConfirmationBody(
                eventTitle = target.title,
                eventDate = eventDate,
                contactName = reservation.contactName,
                seatCount = reservation.seatCount,
                totalPrice = reservation.totalPrice,
            ) }
            if (qrCodeImage != null && cid != null) {
                p { +s.reservationPaymentDetails }
                p { +s.reservationPaymentQrPrompt }
                img {
                    src = "cid:$cid"
                    alt = s.reservationQrAlt
                    width = "200"
                    height = "200"
                    attributes["style"] = "background:white;padding:8px;"
                }
                br
                p { +s.reservationBankTransfer(bankAccount, reservation.variableSymbol) }
            } else if (reservation.paymentType == PaymentInfo.Type.ON_SITE) {
                p { +s.reservationOnSiteNote }
            }
            p { +s.reservationViewLink("$appBaseUrl/reservation/${reservation.id}") }
        } } }

        email.setHtmlMsg(htmlMessage)
        email.setTextMsg(s.reservationHtmlFallback)

        catch({ email.send() }) { e: EmailException ->
            raise(EmailError.SendReservationConfirmationFailed(e.fullMessage()))
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
            raise(EmailError.SendCancellationFailed(e.fullMessage()))
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
            raise(EmailError.SendPaymentConfirmationFailed(e.fullMessage()))
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
            raise(EmailError.SendPaymentNotPaidInFullFailed(e.fullMessage()))
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
            raise(EmailError.SendPasswordResetFailed(e.fullMessage()))
        }
    }
}

class ConsoleEmailService : EmailService {
    override suspend fun sendReservationConfirmation(
        toEmail: String,
        reservation: Reservation,
        target: ReservationTarget,
        bankAccount: String,
        qrCodeImage: ByteArray?,
        icalBytes: ByteArray,
    ): Either<EmailError.SendReservationConfirmation, Unit> {
        println("📧 [MOCK EMAIL] Rezervace na: $toEmail | Akce: ${target.title} | QR: ${qrCodeImage != null}")
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