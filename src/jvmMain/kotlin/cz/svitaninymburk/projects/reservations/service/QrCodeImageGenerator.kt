package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.qr.QrCodeService
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import qrcode.QRCode
import qrcode.color.Colors

interface QrCodeGeneratorService {
    val accountNumber: String
    fun generateQrPng(reservation: Reservation): ByteArray
}

/**
 * Služba specifická pro JVM Backend (Emailing).
 * Používá AWT (Java Graphics) pro vyrenderování PNG obrázku.
 */
class BackendQrCodeGenerator(
    private val qrCodeService: QrCodeService,
    private val settings: AppSettingsProvider,
) : QrCodeGeneratorService {
    override val accountNumber get() = settings.current.bankAccountNumber

    override fun generateQrPng(reservation: Reservation): ByteArray {
        val spaydContent = qrCodeService.generateSpaydString(
            accountNumber = accountNumber,
            amount = reservation.unpaidAmount,
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