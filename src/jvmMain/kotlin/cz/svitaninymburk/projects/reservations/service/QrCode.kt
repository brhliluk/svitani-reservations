package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.util.CzechIbanGenerator
import cz.svitaninymburk.projects.reservations.util.SpaydGenerator
import qrcode.QRCode
import java.io.ByteArrayOutputStream


class QrCodeService(
    val accountNumber: String
) {
    private val myIban: String by lazy { CzechIbanGenerator.toIban(accountNumber) }

    /**
     * Generates a PNG byte array containing the payment QR code.
     */
    fun generateQrPaymentImage(reservation: Reservation): ByteArray {
        // 1. Build the SPAYD content string
        val spaydContent = SpaydGenerator.generate(
            iban = myIban,
            amount = reservation.totalPrice,
            vs = reservation.variableSymbol,
            message = "Rezervace ${reservation.eventInstanceId}"
        )

        // 2. Render the QR code using the library
        val stream = ByteArrayOutputStream()

        QRCode(spaydContent).render().writeImage(stream)

        return stream.toByteArray()
    }
}