package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.qr.QrCodeService
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import qrcode.QRCode
import qrcode.color.Colors

/**
 * Služba specifická pro JVM Backend (Emailing).
 * Používá AWT (Java Graphics) pro vyrenderování PNG obrázku.
 */
class BackendQrCodeGenerator(private val qrCodeService: QrCodeService) {
    val accountNumber get() = qrCodeService.accountNumber

    fun generateQrPng(reservation: Reservation): ByteArray {
        val spaydContent = qrCodeService.generateSpaydString(
            accountNumber = qrCodeService.accountNumber,
            amount = reservation.totalPrice,
            vs = reservation.variableSymbol?.filter { it.isDigit() }?.take(10),
            message = "Rezervace ${reservation.reference.id}"
        )

        return QRCode.ofSquares()
            .withColor(Colors.BLACK)
            .withBackgroundColor(Colors.WHITE)
            .build(spaydContent)
            .renderToBytes()
    }
}