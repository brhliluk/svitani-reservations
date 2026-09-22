package cz.svitaninymburk.projects.reservations.qr

import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import qrcode.QRCode

class QrCodeService {
    fun generateSpaydString(
        accountNumber: String,
        amount: Double,
        vs: String?,
        message: String?
    ): String {
        val iban = CzechIbanGenerator.toIban(accountNumber)
        return SpaydGenerator.generate(iban, amount, vs, message)
    }

    /**
     * Jediné místo, kde vzniká platební SPAYD rezervace. E-mailové PNG, QR v prohlížeči
     * i mobilní API přes něj musí projít — dřív si každý kanál skládal vlastní řetězec
     * a zákazník tak u jedné platby viděl tři různé QR kódy.
     */
    fun reservationSpayd(reservation: Reservation, target: ReservationTarget?, accountNumber: String): String =
        generateSpaydString(
            accountNumber = accountNumber,
            amount = reservation.unpaidAmount,
            vs = reservation.variableSymbol,
            message = paymentMessage(target),
        )

    /**
     * Zpráva pro příjemce. Čte ji Svítání ve výpisu z účtu, ne zákazník, takže je vždycky
     * v jazyce akce a nechodí přes i18n. Datum tu schválně není: SPAYD ze zprávy vyhazuje
     * tečky (viz [SpaydGenerator.sanitizeMessage]) a z `5.10.2026` by ve výpisu zbylo
     * nečitelné `5 10 2026`.
     */
    private fun paymentMessage(target: ReservationTarget?): String? = target?.title

    fun generateReservationPaymentSvg(reservation: Reservation, target: ReservationTarget?, accountNumber: String): String =
        generateQrSvg(reservationSpayd(reservation, target, accountNumber))

    fun generateQrSvg(content: String): String {
        val qrCode = QRCode(content)
        val rawData = qrCode.rawData
        val matrixSize = rawData.size

        val cellSize = 10
        val margin = 4

        val totalSize = (matrixSize + 2 * margin) * cellSize

        return buildString {
            // Hlavička SVG
            append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $totalSize $totalSize" shape-rendering="crispEdges">""")

            // Bílé pozadí (volitelné, ale dobré pro čtečky)
            append("""<rect width="100%" height="100%" fill="#FFFFFF"/>""")

            // Barva QR bodů (černá)
            val darkColor = "#000000"

            // Iterace přes mřížku
            // qrCode.modules je pole Intů, kde 1 = černá, 0 = bílá (záleží na implementaci, obvykle row-major)
            // Knihovna g0dkar vrací lineární pole nebo poskytuje helpery.
            // Nejbezpečnější je použít jejich metodu getModule(x, y)

            for (y in 0 until matrixSize) {
                for (x in 0 until matrixSize) {
                    val square = rawData[y][x]
                    if (square.dark) {
                        val posX = (x + margin) * cellSize
                        val posY = (y + margin) * cellSize
                        append("""<rect x="$posX" y="$posY" width="$cellSize" height="$cellSize" fill="$darkColor"/>""")
                    }
                }
            }

            append("</svg>")
        }
    }
}