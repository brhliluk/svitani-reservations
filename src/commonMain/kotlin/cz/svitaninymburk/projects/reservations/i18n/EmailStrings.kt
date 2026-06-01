package cz.svitaninymburk.projects.reservations.i18n

import kotlinx.datetime.LocalDate

interface EmailStrings {
    // Reservation confirmation
    fun reservationConfirmationSubject(eventTitle: String, eventDate: String): String
    val reservationConfirmationHeading: String
    fun reservationConfirmationBody(eventTitle: String, eventDate: String, contactName: String, seatCount: Int, totalPrice: Double): String
    val reservationPaymentDetails: String
    val reservationPrice: String
    val reservationPaymentQrPrompt: String
    val reservationQrAlt: String
    fun reservationBankTransfer(bankAccount: String, variableSymbol: String?): String
    val reservationPaymentProcessingNote: String
    val reservationHtmlFallback: String
    val reservationOnSiteNote: String
    fun reservationViewLink(url: String): String

    // Cancellation
    fun cancellationSubject(eventTitle: String?): String
    fun cancellationBody(eventTitle: String?): String

    // Payment received
    val paymentReceivedSubject: String
    fun paymentReceivedBody(eventTitle: String?): String

    // Partial payment
    val partialPaymentSubject: String
    fun partialPaymentBody(eventTitle: String?): String
    fun partialPaymentAmount(amount: Double): String
    fun partialPaymentRemaining(amount: Double): String
    val partialPaymentDetails: String

    // Password reset
    val passwordResetSubject: String
    val passwordResetHeading: String
    val passwordResetBody: String
    val passwordResetLinkText: String

    // Lesson reschedule/cancel notifications
    fun lessonRescheduledSubject(seriesTitle: String): String
    fun lessonRescheduledBody(contactName: String, seriesTitle: String, oldDateTime: String, newDateTime: String): String
    fun lessonCancelledSubject(seriesTitle: String): String
    fun lessonCancelledBody(contactName: String, seriesTitle: String, lessonDateTime: String): String

    // Lector notifications
    fun lectorReservationSubject(eventTitle: String): String
    fun lectorReservationBody(contactName: String, contactEmail: String, contactPhone: String?, seatCount: Int, eventTitle: String, occupiedSpots: Int, capacity: Int): String
    fun lectorCancellationSubject(eventTitle: String): String
    fun lectorCancellationBody(contactName: String, eventTitle: String, seatCount: Int, occupiedSpots: Int, capacity: Int): String

    // Lesson opt-out notifications
    fun lessonOptOutSubject(eventTitle: String): String
    fun lessonOptOutBody(eventTitle: String, lessonDate: LocalDate, isLate: Boolean): String
    fun lectorLessonOptOutSubject(eventTitle: String): String
    fun lectorLessonOptOutBody(contactName: String, eventTitle: String, lessonDate: LocalDate, isLate: Boolean): String
}

fun emailStringsFor(locale: String): EmailStrings = when (locale) {
    "en" -> EnEmailStrings
    else -> CsEmailStrings
}

object CsEmailStrings : EmailStrings {
    override fun reservationConfirmationSubject(eventTitle: String, eventDate: String) =
        "Rezervace potvrzena: $eventTitle – $eventDate"
    override val reservationConfirmationHeading = "Děkujeme za rezervaci!"
    override fun reservationConfirmationBody(eventTitle: String, eventDate: String, contactName: String, seatCount: Int, totalPrice: Double) =
        "Dobrý den $contactName,\n\nVaše rezervace na akci $eventTitle ($eventDate) je potvrzena.\nPočet míst: $seatCount\nCelková cena: $totalPrice Kč"
    override val reservationPaymentDetails = "Pokud jste ještě neplatili, platební údaje:"
    override val reservationPrice = "Cena:"
    override val reservationPaymentQrPrompt = "Pro dokončení prosím uhraďte částku pomocí QR kódu níže:"
    override val reservationQrAlt = "QR Platba"
    override fun reservationBankTransfer(bankAccount: String, variableSymbol: String?) =
        "Nebo převodem na účet: $bankAccount, VS: $variableSymbol"
    override val reservationPaymentProcessingNote = "Platba bude zpracována do 10 minut od přepsání na náš účet."
    override val reservationHtmlFallback = "Váš klient nepodporuje HTML emaily."
    override val reservationOnSiteNote = "Platbu prosím uhraďte v hotovosti na místě."
    override fun reservationViewLink(url: String) = "Zobrazit rezervaci: $url"
    override fun cancellationSubject(eventTitle: String?) = "Zrušení rezervace: $eventTitle"
    override fun cancellationBody(eventTitle: String?) = "Vaše rezervace na akci: $eventTitle byla zrušena."
    override val paymentReceivedSubject = "Potvrzení platby"
    override fun paymentReceivedBody(eventTitle: String?) = "Vaše rezervace na akci: $eventTitle byla zaplacena, děkujeme!"
    override val partialPaymentSubject = "Částečně zaplaceno"
    override fun partialPaymentBody(eventTitle: String?) = "Vaše rezervace na akci: $eventTitle byla zaplacena pouze částečně!"
    override fun partialPaymentAmount(amount: Double) = "Zaznamenali jsme platbu v hodnotě: $amount"
    override fun partialPaymentRemaining(amount: Double) = "Zbývá doplatit: $amount"
    override val partialPaymentDetails = "Platební údaje k doplacení platby:"
    override val passwordResetSubject = "Změna hesla"
    override val passwordResetHeading = "Změna hesla"
    override val passwordResetBody = "Pro změnu hesla klikněte na následující odkaz:"
    override val passwordResetLinkText = "rezervace.svitaninymburk.cz/reset"
    override fun lessonRescheduledSubject(seriesTitle: String) = "Přeplánování lekce: $seriesTitle"
    override fun lessonRescheduledBody(contactName: String, seriesTitle: String, oldDateTime: String, newDateTime: String) =
        "Dobrý den $contactName,\n\nlekce kurzu $seriesTitle byla přeplánována.\nPůvodní termín: $oldDateTime\nNový termín: $newDateTime"
    override fun lessonCancelledSubject(seriesTitle: String) = "Zrušení lekce: $seriesTitle"
    override fun lessonCancelledBody(contactName: String, seriesTitle: String, lessonDateTime: String) =
        "Dobrý den $contactName,\n\nlekce kurzu $seriesTitle dne $lessonDateTime byla zrušena."
    override fun lectorReservationSubject(eventTitle: String) = "Nová rezervace: $eventTitle"
    override fun lectorReservationBody(contactName: String, contactEmail: String, contactPhone: String?, seatCount: Int, eventTitle: String, occupiedSpots: Int, capacity: Int): String {
        val phone = if (contactPhone != null) "\nTelefon: $contactPhone" else ""
        return "Nová rezervace na akci: $eventTitle\n\nZákazník: $contactName\nE-mail: $contactEmail$phone\nPočet míst: $seatCount\n\nObsazenost: $occupiedSpots / $capacity míst"
    }
    override fun lectorCancellationSubject(eventTitle: String) = "Zrušená rezervace: $eventTitle"
    override fun lectorCancellationBody(contactName: String, eventTitle: String, seatCount: Int, occupiedSpots: Int, capacity: Int) =
        "Rezervace na akci $eventTitle byla zrušena.\n\nZákazník: $contactName\nUvolněná místa: $seatCount\n\nObsazenost: $occupiedSpots / $capacity míst"
    override fun lessonOptOutSubject(eventTitle: String) = "Odhlášení z lekce: $eventTitle"
    override fun lessonOptOutBody(eventTitle: String, lessonDate: LocalDate, isLate: Boolean): String {
        val base = "Odhlásili jste se z lekce kurzu \"$eventTitle\" dne $lessonDate."
        val lateNote = if (isLate) "\n\nUpozornění: Odhlášení proběhlo po termínu (po 18:00 předchozího dne)." else ""
        return base + lateNote
    }
    override fun lectorLessonOptOutSubject(eventTitle: String) = "Odhlášení z lekce: $eventTitle"
    override fun lectorLessonOptOutBody(contactName: String, eventTitle: String, lessonDate: LocalDate, isLate: Boolean): String {
        val lateNote = if (isLate) " (pozdní odhlášení)" else ""
        return "$contactName se odhlásil/a z lekce kurzu \"$eventTitle\" dne $lessonDate$lateNote."
    }
}

object EnEmailStrings : EmailStrings {
    override fun reservationConfirmationSubject(eventTitle: String, eventDate: String) =
        "Reservation confirmed: $eventTitle – $eventDate"
    override val reservationConfirmationHeading = "Thank you for your reservation!"
    override fun reservationConfirmationBody(eventTitle: String, eventDate: String, contactName: String, seatCount: Int, totalPrice: Double) =
        "Hello $contactName,\n\nYour reservation for $eventTitle ($eventDate) is confirmed.\nSeats: $seatCount\nTotal: $totalPrice CZK"
    override val reservationPaymentDetails = "If you haven't paid yet, here are the payment details:"
    override val reservationPrice = "Price:"
    override val reservationPaymentQrPrompt = "To complete your reservation, please pay using the QR code below:"
    override val reservationQrAlt = "QR Payment"
    override fun reservationBankTransfer(bankAccount: String, variableSymbol: String?) =
        "Or by bank transfer to: $bankAccount, VS: $variableSymbol"
    override val reservationPaymentProcessingNote = "Your payment will be processed within 10 minutes of being received in our account."
    override val reservationHtmlFallback = "Your email client does not support HTML emails."
    override val reservationOnSiteNote = "Please pay in cash on the day of the event."
    override fun reservationViewLink(url: String) = "View reservation: $url"
    override fun cancellationSubject(eventTitle: String?) = "Reservation cancelled: $eventTitle"
    override fun cancellationBody(eventTitle: String?) = "Your reservation for: $eventTitle has been cancelled."
    override val paymentReceivedSubject = "Payment confirmed"
    override fun paymentReceivedBody(eventTitle: String?) = "Your reservation for: $eventTitle has been paid, thank you!"
    override val partialPaymentSubject = "Partially paid"
    override fun partialPaymentBody(eventTitle: String?) = "Your reservation for: $eventTitle has only been partially paid!"
    override fun partialPaymentAmount(amount: Double) = "We have recorded a payment of: $amount"
    override fun partialPaymentRemaining(amount: Double) = "Remaining amount to pay: $amount"
    override val partialPaymentDetails = "Payment details for the remaining amount:"
    override val passwordResetSubject = "Password reset"
    override val passwordResetHeading = "Password reset"
    override val passwordResetBody = "To reset your password, click the following link:"
    override val passwordResetLinkText = "rezervace.svitaninymburk.cz/reset"
    override fun lessonRescheduledSubject(seriesTitle: String) = "Lesson rescheduled: $seriesTitle"
    override fun lessonRescheduledBody(contactName: String, seriesTitle: String, oldDateTime: String, newDateTime: String) =
        "Hello $contactName,\n\na lesson of $seriesTitle has been rescheduled.\nOriginal: $oldDateTime\nNew: $newDateTime"
    override fun lessonCancelledSubject(seriesTitle: String) = "Lesson cancelled: $seriesTitle"
    override fun lessonCancelledBody(contactName: String, seriesTitle: String, lessonDateTime: String) =
        "Hello $contactName,\n\nthe lesson of $seriesTitle on $lessonDateTime has been cancelled."
    override fun lectorReservationSubject(eventTitle: String) = "New booking: $eventTitle"
    override fun lectorReservationBody(contactName: String, contactEmail: String, contactPhone: String?, seatCount: Int, eventTitle: String, occupiedSpots: Int, capacity: Int): String {
        val phone = if (contactPhone != null) "\nPhone: $contactPhone" else ""
        return "New booking for: $eventTitle\n\nCustomer: $contactName\nEmail: $contactEmail$phone\nSeats: $seatCount\n\nOccupancy: $occupiedSpots / $capacity spots"
    }
    override fun lectorCancellationSubject(eventTitle: String) = "Cancelled booking: $eventTitle"
    override fun lectorCancellationBody(contactName: String, eventTitle: String, seatCount: Int, occupiedSpots: Int, capacity: Int) =
        "A booking for $eventTitle has been cancelled.\n\nCustomer: $contactName\nFreed seats: $seatCount\n\nOccupancy: $occupiedSpots / $capacity spots"
    override fun lessonOptOutSubject(eventTitle: String) = "Lesson unsubscription: $eventTitle"
    override fun lessonOptOutBody(eventTitle: String, lessonDate: LocalDate, isLate: Boolean): String {
        val base = "You have unsubscribed from a lesson of \"$eventTitle\" on $lessonDate."
        val lateNote = if (isLate) "\n\nNote: This was a late cancellation (after 6:00 PM the previous day)." else ""
        return base + lateNote
    }
    override fun lectorLessonOptOutSubject(eventTitle: String) = "Lesson unsubscription: $eventTitle"
    override fun lectorLessonOptOutBody(contactName: String, eventTitle: String, lessonDate: LocalDate, isLate: Boolean): String {
        val lateNote = if (isLate) " (late cancellation)" else ""
        return "$contactName unsubscribed from a lesson of \"$eventTitle\" on $lessonDate$lateNote."
    }
}
