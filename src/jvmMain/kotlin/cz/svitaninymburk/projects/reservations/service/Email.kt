package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.right
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.i18n.LectorTarget
import cz.svitaninymburk.projects.reservations.i18n.emailStringsFor
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.util.PhoneNumber
import cz.svitaninymburk.projects.reservations.util.humanReadable
import com.sun.mail.smtp.SMTPAddressFailedException
import com.sun.mail.smtp.SMTPSendFailedException
import com.sun.mail.util.MailConnectException
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.h1
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.stream.appendHTML
import kotlinx.html.strong
import kotlinx.html.ul
import kotlinx.datetime.LocalDateTime
import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.EmailException
import org.apache.commons.mail.HtmlEmail
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.mail.util.ByteArrayDataSource
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid


/**
 * Pauzy mezi pokusy o odeslání; počet pokusů je tedy o jeden vyšší.
 *
 * Gmail občas odpoví `451-4.3.0 Mail server temporarily rejected message` a jediný
 * pokus takový mail nenávratně ztratí — zbyde po něm jen FAILURE v historii a nikdo
 * ho už nepošle. Delší čekání ale smysl nemá: maily se posílají uvnitř požadavku,
 * takže každá pauza drží admina nebo zákazníka na odeslaném formuláři.
 */
private val SEND_RETRY_DELAYS = listOf(2.seconds, 5.seconds)

/**
 * Opakovat má smysl jen to, co je dočasné: SMTP odpověď 4xx a chyby spojení.
 * Špatná adresa, odmítnutá autentizace nebo 5xx se opakováním nespraví a jen by
 * prodloužily požadavek o dalších sedm sekund.
 *
 * Chodí se po `cause` — `EmailException` z commons-email je jen obal, skutečnou
 * příčinu (a u SMTP i návratový kód) nese až zabalená výjimka z javax.mail.
 */
internal fun Throwable.isTransientSmtpFailure(): Boolean {
    var cause: Throwable? = this
    while (cause != null) {
        val transient = when (cause) {
            is SMTPSendFailedException -> cause.returnCode in 400..499
            is SMTPAddressFailedException -> cause.returnCode in 400..499
            is MailConnectException, is SocketTimeoutException, is ConnectException, is UnknownHostException -> true
            else -> false
        }
        if (transient) return true
        cause = cause.cause
    }
    return false
}


class GmailEmailService(
    private val settings: AppSettingsProvider,
    private val appBaseUrl: String,
    private val eventRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
) : EmailService, LectorEmailService, WalletEmailService {

    private val logger = KtorSimpleLogger(this::class.jvmName)

    /**
     * Odešle mail a při dočasném odmítnutí to zkusí znovu — viz [SEND_RETRY_DELAYS].
     *
     * MIME zpráva se staví jen jednou: `Email.send()` volané podruhé spadne na
     * „The MimeMessage is already built.“, takže opakovat se smí samotné odeslání,
     * ne celý `send()`. Chyba z posledního pokusu propadne ven stejně jako dřív.
     */
    private suspend fun HtmlEmail.sendWithRetry() {
        buildMimeMessage()
        SEND_RETRY_DELAYS.forEachIndexed { index, pause ->
            try {
                sendMimeMessage()
                return
            } catch (e: EmailException) {
                if (!e.isTransientSmtpFailure()) throw e
                logger.warn(
                    "SMTP odmítl zprávu dočasně (pokus ${index + 1}/${SEND_RETRY_DELAYS.size + 1}), " +
                        "zkusím znovu za $pause: ${e.fullMessage()}"
                )
                delay(pause)
            }
        }
        sendMimeMessage()
    }

    /**
     * Název akce pro platební maily. Přihláška na kurz je rezervace na sérii, ne na
     * instanci — hledat ji jen mezi lekcemi znamenalo "Vaše rezervace na akci: null".
     */
    internal suspend fun titleFor(reservation: Reservation): String? = when (val ref = reservation.reference) {
        is Reference.Instance -> eventRepository.get(ref.id)?.title
        is Reference.Series -> eventSeriesRepository.get(ref.id)?.title
    }

    private fun EmailException.fullMessage(): String = buildString {
        var t: Throwable? = this@fullMessage
        while (t != null) {
            if (isNotEmpty()) append(" → ")
            append(t::class.simpleName).append(": ").append(t.message)
            t = t.cause
        }
    }

    private fun setupEmail(): HtmlEmail {
        val s = settings.current
        val email = HtmlEmail()
        email.hostName = "smtp.gmail.com"
        email.setSslSmtpPort("465")
        email.setAuthenticator(DefaultAuthenticator(s.senderEmail, s.gmailAppPassword))
        email.isSSLOnConnect = true
        email.setCharset("UTF-8")
        email.setFrom(s.senderEmail, s.senderDisplayName)
        return email
    }

    override suspend fun sendReservationConfirmation(
        toEmail: String,
        reservation: Reservation,
        target: ReservationTarget,
        bankAccount: String,
        qrCodeImage: ByteArray?,
        icalBytes: ByteArray,
    ): Either<EmailError.SendReservationConfirmation, Unit> = either { withContext(Dispatchers.IO) { catch({
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
                p { +s.reservationPaymentProcessingNote }
            } else if (reservation.paymentType == PaymentType.ON_SITE) {
                p { +s.reservationOnSiteNote }
            }
            p { +s.reservationViewLink("$appBaseUrl/reservation/${reservation.id}") }
        } } }

        email.setHtmlMsg(htmlMessage)
        email.setTextMsg(s.reservationHtmlFallback)

        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendReservationConfirmationFailed(e.fullMessage()))
    } } }

    override suspend fun sendCancellationNotice(toEmail: String, eventTitle: String, reservationId: kotlin.uuid.Uuid, locale: String): Either<EmailError.SendCancellation, Unit> = either { withContext(Dispatchers.IO) { catch({
        val s = emailStringsFor(locale)
        val email = setupEmail()
        email.addTo(toEmail)

        val url = "$appBaseUrl/reservation/$reservationId"
        email.subject = s.cancellationSubject(eventTitle)
        email.setHtmlMsg(buildString { appendHTML().html { body {
            p { +s.cancellationBody(eventTitle) }
            p { +s.reservationViewLink(url) }
        } } })
        email.setTextMsg(s.cancellationBody(eventTitle) + "\n" + s.reservationViewLink(url))

        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendCancellationFailed(e.fullMessage()))
    } } }

    override suspend fun sendPaymentReceivedConfirmation(reservation: Reservation): Either<EmailError.SendPaymentConfirmation, Unit> = either { withContext(Dispatchers.IO) { catch({
        val s = emailStringsFor(reservation.locale)
        val email = setupEmail()
        email.addTo(reservation.contactEmail)
        email.subject = s.paymentReceivedSubject

        val eventTitle = titleFor(reservation)
        val url = "$appBaseUrl/reservation/${reservation.id}"

        email.setHtmlMsg(buildString { appendHTML().html { body {
            p { +s.paymentReceivedBody(eventTitle) }
            p { +s.reservationViewLink(url) }
        } } })
        email.setTextMsg(s.paymentReceivedBody(eventTitle) + "\n" + s.reservationViewLink(url))

        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendPaymentConfirmationFailed(e.fullMessage()))
    } } }

    override suspend fun sendPaymentNotPaidInFull(
        reservation: Reservation,
        paymentInfo: BankTransaction,
        bankAccount: String,
        qrCodeImage: ByteArray,
    ): Either<EmailError.SendPaymentNotPaidInFull, Unit> = either { withContext(Dispatchers.IO) { catch({
        val s = emailStringsFor(reservation.locale)
        val email = setupEmail()
        email.addTo(reservation.contactEmail)
        email.subject = s.partialPaymentSubject

        val eventTitle = titleFor(reservation)

        val dataSource = ByteArrayDataSource(qrCodeImage, "image/png")
        val cid = email.embed(dataSource, "qr-code-platba")

        email.setHtmlMsg(buildString { appendHTML().html { body {
            p { +s.partialPaymentBody(eventTitle) }
            p { +s.partialPaymentAmount(paymentInfo.amount) }
            p { +s.partialPaymentRemaining(reservation.unpaidAmount) }
            p { +s.partialPaymentDetails }
            img {
                src = "cid:$cid"
                alt = s.reservationQrAlt
                width = "200"
                height = "200"
            }
            br
            p { +s.reservationBankTransfer(bankAccount, reservation.variableSymbol) }
            p { +s.reservationViewLink("$appBaseUrl/reservation/${reservation.id}") }
        } } })

        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendPaymentNotPaidInFullFailed(e.fullMessage()))
    } } }

    override suspend fun sendPasswordResetEmail(toEmail: String, resetToken: String): Either<EmailError.SendPasswordReset, Unit> = either { withContext(Dispatchers.IO) { catch({
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

        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendPasswordResetFailed(e.fullMessage()))
    } } }

    override suspend fun sendReservationClaimEmail(
        toEmail: String,
        reservations: List<MyReservationListItem>,
        claimToken: String,
        locale: String,
    ): Either<EmailError.SendReservationClaim, Unit> = either { withContext(Dispatchers.IO) { catch({
        val s = emailStringsFor(locale)
        val email = setupEmail()
        email.addTo(toEmail)
        email.subject = s.reservationClaimSubject

        email.setHtmlMsg(buildString { appendHTML().html { body {
            h1 { +s.reservationClaimHeading }

            p { +s.reservationClaimBody(reservations.size) }
            ul {
                reservations.forEach { item ->
                    li { +"${item.eventTitle} — ${item.startDateTime.humanReadable}" }
                }
            }
            a {
                // href musí jít před obsah — streamovací builder atribut po prvním
                // vloženém uzlu odmítne ("already passed to the downstream").
                href = "$appBaseUrl/claim-reservations/$claimToken"
                +s.reservationClaimLinkText
            }
            p { +s.reservationClaimIgnoreNote }
        } } })

        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendReservationClaimFailed(e.fullMessage()))
    } } }

    override suspend fun sendLessonRescheduledNotification(
        toEmail: String,
        contactName: String,
        seriesTitle: String,
        oldDateTime: LocalDateTime,
        newDateTime: LocalDateTime,
        locale: String,
    ): Either<EmailError.SendLessonRescheduled, Unit> = either { withContext(Dispatchers.IO) { catch({
        val email = setupEmail()
        val s = emailStringsFor(locale)
        email.addTo(toEmail)
        email.subject = s.lessonRescheduledSubject(seriesTitle)
        email.setTextMsg(s.lessonRescheduledBody(contactName, seriesTitle, oldDateTime.humanReadable, newDateTime.humanReadable))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendLessonRescheduledFailed(e.fullMessage()))
    } } }

    override suspend fun sendLessonCancelledNotification(
        toEmail: String,
        contactName: String,
        seriesTitle: String,
        lessonDateTime: LocalDateTime,
        locale: String,
    ): Either<EmailError.SendLessonCancelled, Unit> = either { withContext(Dispatchers.IO) { catch({
        val email = setupEmail()
        val s = emailStringsFor(locale)
        email.addTo(toEmail)
        email.subject = s.lessonCancelledSubject(seriesTitle)
        email.setTextMsg(s.lessonCancelledBody(contactName, seriesTitle, lessonDateTime.humanReadable))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendLessonCancelledFailed(e.fullMessage()))
    } } }

    override suspend fun sendRemainingLessonsCancelledNotification(
        toEmail: String,
        contactName: String,
        seriesTitle: String,
        lessonDateTimes: List<LocalDateTime>,
        locale: String,
    ): Either<EmailError.SendLessonCancelled, Unit> = either { withContext(Dispatchers.IO) { catch({
        val email = setupEmail()
        val s = emailStringsFor(locale)
        email.addTo(toEmail)
        email.subject = s.remainingLessonsCancelledSubject(seriesTitle)
        email.setTextMsg(s.remainingLessonsCancelledBody(contactName, seriesTitle, lessonDateTimes.map { it.humanReadable }))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendLessonCancelledFailed(e.fullMessage()))
    } } }

    override suspend fun sendLectorReservationNotification(
        lectorEmail: String,
        contactName: String,
        contactEmail: String,
        contactPhone: String?,
        seatCount: Int,
        eventTitle: String,
        target: LectorTarget,
        occupiedSpots: Int,
        capacity: Int,
        locale: String,
    ): Either<EmailError.SendLectorReservation, Unit> = either { withContext(Dispatchers.IO) { catch({
        val s = emailStringsFor(locale)
        val email = setupEmail()
        email.addTo(lectorEmail)
        email.subject = s.lectorReservationSubject(eventTitle, target)
        val formattedPhone = contactPhone?.let { PhoneNumber.format(it) }
        email.setTextMsg(s.lectorReservationBody(contactName, contactEmail, formattedPhone, seatCount, eventTitle, target, occupiedSpots, capacity))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendLectorReservationFailed(e.fullMessage()))
    } } }

    override suspend fun sendLectorCancellationNotification(
        lectorEmail: String,
        contactName: String,
        eventTitle: String,
        target: LectorTarget,
        seatCount: Int,
        occupiedSpots: Int,
        capacity: Int,
        locale: String,
    ): Either<EmailError.SendLectorCancellation, Unit> = either { withContext(Dispatchers.IO) { catch({
        val s = emailStringsFor(locale)
        val email = setupEmail()
        email.addTo(lectorEmail)
        email.subject = s.lectorCancellationSubject(eventTitle, target)
        email.setTextMsg(s.lectorCancellationBody(contactName, eventTitle, target, seatCount, occupiedSpots, capacity))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendLectorCancellationFailed(e.fullMessage()))
    } } }

    override suspend fun sendLessonOptOutNotice(
        toEmail: String,
        eventTitle: String,
        lessonDate: kotlinx.datetime.LocalDate,
        isLateCancellation: Boolean,
        locale: String,
    ): Either<EmailError.SendCancellation, Unit> = either { withContext(Dispatchers.IO) { catch({
        val s = emailStringsFor(locale)
        val email = setupEmail()
        email.addTo(toEmail)
        email.subject = s.lessonOptOutSubject(eventTitle)
        email.setTextMsg(s.lessonOptOutBody(eventTitle, lessonDate, isLateCancellation))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendCancellationFailed(e.fullMessage()))
    } } }

    override suspend fun sendLectorLessonOptOutNotification(
        lectorEmail: String,
        contactName: String,
        eventTitle: String,
        lessonDate: kotlinx.datetime.LocalDate,
        isLateCancellation: Boolean,
        locale: String,
    ): Either<EmailError.SendLectorCancellation, Unit> = either { withContext(Dispatchers.IO) { catch({
        val s = emailStringsFor(locale)
        val email = setupEmail()
        email.addTo(lectorEmail)
        email.subject = s.lectorLessonOptOutSubject(eventTitle)
        email.setTextMsg(s.lectorLessonOptOutBody(contactName, eventTitle, lessonDate, isLateCancellation))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendLectorCancellationFailed(e.fullMessage()))
    } } }

    override suspend fun sendWalletCredited(
        toEmail: String,
        walletCode: String,
        creditedAmount: Double,
        newBalance: Double,
        resetMonth: Int,
        resetDay: Int,
        locale: String,
    ): Either<EmailError.SendWallet, Unit> = either { withContext(Dispatchers.IO) { catch({
        val strings = emailStringsFor(locale)
        val resetDate = "%02d. %02d.".format(resetDay, resetMonth)
        val walletLink = "$appBaseUrl/wallet/$walletCode"
        val email = setupEmail()
        email.addTo(toEmail)
        email.subject = strings.walletCreditedSubject("%.0f".format(creditedAmount))
        email.setHtmlMsg(strings.walletCreditedBody(walletCode, "%.0f".format(creditedAmount), "%.0f".format(newBalance), resetDate, walletLink))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendWalletFailed(e.fullMessage()))
    } } }

    override suspend fun sendWalletApplied(
        toEmail: String,
        walletCode: String,
        deductedAmount: Double,
        remainingBalance: Double,
        locale: String,
    ): Either<EmailError.SendWallet, Unit> = either { withContext(Dispatchers.IO) { catch({
        val strings = emailStringsFor(locale)
        val walletLink = "$appBaseUrl/wallet/$walletCode"
        val email = setupEmail()
        email.addTo(toEmail)
        email.subject = strings.walletAppliedSubject()
        email.setHtmlMsg(strings.walletAppliedBody(walletCode, "%.0f".format(deductedAmount), "%.0f".format(remainingBalance), walletLink))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendWalletFailed(e.fullMessage()))
    } } }

    override suspend fun sendWalletResetWarning(
        toEmail: String,
        walletCode: String,
        currentBalance: Double,
        resetMonth: Int,
        resetDay: Int,
        locale: String,
    ): Either<EmailError.SendWallet, Unit> = either { withContext(Dispatchers.IO) { catch({
        val strings = emailStringsFor(locale)
        val resetDate = "%02d. %02d.".format(resetDay, resetMonth)
        val walletLink = "$appBaseUrl/wallet/$walletCode"
        val email = setupEmail()
        email.addTo(toEmail)
        email.subject = strings.walletResetWarningSubject(resetDate)
        email.setHtmlMsg(strings.walletResetWarningBody("%.0f".format(currentBalance), walletCode, resetDate, walletLink))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendWalletFailed(e.fullMessage()))
    } } }

    override suspend fun sendWaitlistConfirmation(
        toEmail: String,
        eventTitle: String,
        contactName: String,
        reservationId: Uuid,
        locale: String,
    ): Either<EmailError.SendWaitlistConfirmation, Unit> = either { withContext(Dispatchers.IO) { catch({
        val s = emailStringsFor(locale)
        val email = setupEmail()
        email.addTo(toEmail)
        email.subject = s.waitlistConfirmationSubject(eventTitle)
        val url = "$appBaseUrl/reservation/$reservationId"
        email.setHtmlMsg(buildString { appendHTML().html { body {
            h1 { +s.waitlistConfirmationHeading }
            p { +s.waitlistConfirmationBody(eventTitle, contactName) }
            p { +s.reservationViewLink(url) }
        } } })
        email.setTextMsg(s.waitlistConfirmationBody(eventTitle, contactName) + "\n" + s.reservationViewLink(url))
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendWaitlistConfirmationFailed(e.fullMessage()))
    } } }

    override suspend fun sendWaitlistPromotion(
        toEmail: String,
        reservation: Reservation,
        target: ReservationTarget,
        bankAccount: String,
        qrCodeImage: ByteArray?,
        icalBytes: ByteArray,
    ): Either<EmailError.SendWaitlistPromotion, Unit> = either { withContext(Dispatchers.IO) { catch({
        val email = setupEmail()
        val s = emailStringsFor(reservation.locale)
        email.addTo(toEmail)
        val eventDate = target.startDateTime.humanReadable
        email.subject = s.waitlistPromotionSubject(target.title, eventDate)

        val cid: String? = if (qrCodeImage != null) {
            email.embed(ByteArrayDataSource(qrCodeImage, "image/png"), "qr-code-platba")
        } else null
        email.attach(ByteArrayDataSource(icalBytes, "text/calendar; charset=UTF-8; method=REQUEST"), "rezervace.ics", "Rezervace do kalendáře")

        val htmlMessage = buildString { appendHTML().html { body {
            h1 { +s.waitlistPromotionHeading }
            p { +s.waitlistPromotionIntro(reservation.contactName, target.title) }
            p { +s.reservationConfirmationBody(
                eventTitle = target.title,
                eventDate = eventDate,
                contactName = reservation.contactName,
                seatCount = reservation.seatCount,
                totalPrice = reservation.totalPrice,
            ) }
            if (qrCodeImage != null && cid != null) {
                p { +s.reservationPaymentQrPrompt }
                img { src = "cid:$cid"; alt = s.reservationQrAlt; width = "200"; height = "200"; attributes["style"] = "background:white;padding:8px;" }
                br
                p { +s.reservationBankTransfer(bankAccount, reservation.variableSymbol) }
                p { +s.reservationPaymentProcessingNote }
            } else if (reservation.paymentType == PaymentType.ON_SITE) {
                p { +s.reservationOnSiteNote }
            }
            p { +s.reservationViewLink("$appBaseUrl/reservation/${reservation.id}") }
        } } }
        email.setHtmlMsg(htmlMessage)
        email.setTextMsg(s.reservationHtmlFallback)
        email.sendWithRetry()
    }) { e: EmailException ->
        raise(EmailError.SendWaitlistPromotionFailed(e.fullMessage()))
    } } }
}

class ConsoleEmailService : EmailService, LectorEmailService, WalletEmailService {
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
        eventTitle: String,
        reservationId: kotlin.uuid.Uuid,
        locale: String,
    ): Either<EmailError.SendCancellation, Unit> {
        println("📧 [MOCK EMAIL] Odesílám storno na: $toEmail | Akce: $eventTitle | Rezervace: $reservationId")
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
        qrCodeImage: ByteArray
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

    override suspend fun sendReservationClaimEmail(
        toEmail: String,
        reservations: List<MyReservationListItem>,
        claimToken: String,
        locale: String,
    ): Either<EmailError.SendReservationClaim, Unit> {
        println("📧 [MOCK EMAIL] Přidání ${reservations.size} rezervací k účtu: $toEmail")
        println("   👉 /claim-reservations/$claimToken")
        return Unit.right()
    }

    override suspend fun sendLessonRescheduledNotification(
        toEmail: String, contactName: String, seriesTitle: String,
        oldDateTime: LocalDateTime, newDateTime: LocalDateTime, locale: String,
    ): Either<EmailError.SendLessonRescheduled, Unit> {
        println("📧 [MOCK] Lekce přeplánována: $seriesTitle | $toEmail | ${oldDateTime} → ${newDateTime}")
        return Unit.right()
    }

    override suspend fun sendLessonCancelledNotification(
        toEmail: String, contactName: String, seriesTitle: String,
        lessonDateTime: LocalDateTime, locale: String,
    ): Either<EmailError.SendLessonCancelled, Unit> {
        println("📧 [MOCK] Lekce zrušena: $seriesTitle | $toEmail | $lessonDateTime")
        return Unit.right()
    }

    override suspend fun sendRemainingLessonsCancelledNotification(
        toEmail: String, contactName: String, seriesTitle: String,
        lessonDateTimes: List<LocalDateTime>, locale: String,
    ): Either<EmailError.SendLessonCancelled, Unit> {
        println("📧 [MOCK] Zbytek kurzu zrušen: $seriesTitle | $toEmail | ${lessonDateTimes.size} lekcí")
        return Unit.right()
    }

    override suspend fun sendLectorReservationNotification(
        lectorEmail: String, contactName: String, contactEmail: String, contactPhone: String?,
        seatCount: Int, eventTitle: String, target: LectorTarget, occupiedSpots: Int, capacity: Int, locale: String,
    ): Either<EmailError.SendLectorReservation, Unit> {
        println("[LECTOR EMAIL] To: $lectorEmail | New booking for '$eventTitle' ($target) | Customer: $contactName ($contactEmail${if (contactPhone != null) ", ${PhoneNumber.format(contactPhone)}" else ""}) | Seats: $seatCount | Occupancy: $occupiedSpots/$capacity")
        return Unit.right()
    }

    override suspend fun sendLectorCancellationNotification(
        lectorEmail: String, contactName: String, eventTitle: String, target: LectorTarget,
        seatCount: Int, occupiedSpots: Int, capacity: Int, locale: String,
    ): Either<EmailError.SendLectorCancellation, Unit> {
        println("[LECTOR EMAIL] To: $lectorEmail | Cancelled booking for '$eventTitle' ($target) | Customer: $contactName | Freed: $seatCount | Occupancy: $occupiedSpots/$capacity")
        return Unit.right()
    }

    override suspend fun sendLessonOptOutNotice(
        toEmail: String,
        eventTitle: String,
        lessonDate: kotlinx.datetime.LocalDate,
        isLateCancellation: Boolean,
        locale: String,
    ): Either<EmailError.SendCancellation, Unit> {
        println("📧 [MOCK] Lesson opt-out → $toEmail | $eventTitle | $lessonDate | late=$isLateCancellation")
        return Unit.right()
    }

    override suspend fun sendLectorLessonOptOutNotification(
        lectorEmail: String,
        contactName: String,
        eventTitle: String,
        lessonDate: kotlinx.datetime.LocalDate,
        isLateCancellation: Boolean,
        locale: String,
    ): Either<EmailError.SendLectorCancellation, Unit> {
        println("📧 [MOCK] Lector lesson opt-out → $lectorEmail | $contactName | $eventTitle | $lessonDate")
        return Unit.right()
    }

    override suspend fun sendWalletCredited(
        toEmail: String, walletCode: String, creditedAmount: Double,
        newBalance: Double, resetMonth: Int, resetDay: Int, locale: String,
    ): Either<EmailError.SendWallet, Unit> {
        println("[EMAIL] Wallet credited: $walletCode +$creditedAmount → balance: $newBalance")
        return Unit.right()
    }

    override suspend fun sendWalletApplied(
        toEmail: String, walletCode: String, deductedAmount: Double, remainingBalance: Double, locale: String,
    ): Either<EmailError.SendWallet, Unit> {
        println("[EMAIL] Wallet applied: $walletCode -$deductedAmount remaining: $remainingBalance")
        return Unit.right()
    }

    override suspend fun sendWalletResetWarning(
        toEmail: String, walletCode: String, currentBalance: Double,
        resetMonth: Int, resetDay: Int, locale: String,
    ): Either<EmailError.SendWallet, Unit> {
        println("[EMAIL] Wallet reset warning: $walletCode balance: $currentBalance")
        return Unit.right()
    }

    override suspend fun sendWaitlistConfirmation(toEmail: String, eventTitle: String, contactName: String, reservationId: Uuid, locale: String): Either<EmailError.SendWaitlistConfirmation, Unit> {
        println("📧 [MOCK] Waitlist confirmation → $toEmail | $eventTitle")
        return Unit.right()
    }

    override suspend fun sendWaitlistPromotion(toEmail: String, reservation: Reservation, target: ReservationTarget, bankAccount: String, qrCodeImage: ByteArray?, icalBytes: ByteArray): Either<EmailError.SendWaitlistPromotion, Unit> {
        println("📧 [MOCK] Waitlist promotion → $toEmail | ${target.title}")
        return Unit.right()
    }
}