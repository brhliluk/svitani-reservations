package cz.svitaninymburk.projects.reservations.i18n

interface EmailStrings {
    // Reservation confirmation
    fun reservationConfirmationSubject(id: String): String
    val reservationConfirmationHeading: String
    fun reservationConfirmationBody(eventTitle: String): String
    val reservationPaymentDetails: String
    val reservationPrice: String
    val reservationPaymentQrPrompt: String
    val reservationQrAlt: String
    fun reservationBankTransfer(bankAccount: String, variableSymbol: String?): String
    val reservationHtmlFallback: String

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
}

fun emailStringsFor(locale: String): EmailStrings = when (locale) {
    "en" -> EnEmailStrings
    else -> CsEmailStrings
}

object CsEmailStrings : EmailStrings {
    override fun reservationConfirmationSubject(id: String) = "Potvrzení rezervace: $id"
    override val reservationConfirmationHeading = "Děkujeme za rezervaci!"
    override fun reservationConfirmationBody(eventTitle: String) = "Vaše místa na akci: $eventTitle jsou zarezervována."
    override val reservationPaymentDetails = "Pokud jste ještě neplatili, platební údaje:"
    override val reservationPrice = "Cena:"
    override val reservationPaymentQrPrompt = "Pro dokončení prosím uhraďte částku pomocí QR kódu níže:"
    override val reservationQrAlt = "QR Platba"
    override fun reservationBankTransfer(bankAccount: String, variableSymbol: String?) =
        "Nebo převodem na účet: $bankAccount, VS: $variableSymbol"
    override val reservationHtmlFallback = "Váš klient nepodporuje HTML emaily."

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
}

object EnEmailStrings : EmailStrings {
    override fun reservationConfirmationSubject(id: String) = "Reservation confirmation: $id"
    override val reservationConfirmationHeading = "Thank you for your reservation!"
    override fun reservationConfirmationBody(eventTitle: String) = "Your seats for: $eventTitle are reserved."
    override val reservationPaymentDetails = "If you haven't paid yet, here are the payment details:"
    override val reservationPrice = "Price:"
    override val reservationPaymentQrPrompt = "To complete your reservation, please pay using the QR code below:"
    override val reservationQrAlt = "QR Payment"
    override fun reservationBankTransfer(bankAccount: String, variableSymbol: String?) =
        "Or by bank transfer to: $bankAccount, VS: $variableSymbol"
    override val reservationHtmlFallback = "Your email client does not support HTML emails."

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
}
