package cz.svitaninymburk.projects.reservations.qr

import cz.svitaninymburk.projects.reservations.reservation.Reservation
import qrcode.QRCode

class QrCodeService(val accountNumber: String) {
    private val iban: String by lazy { CzechIbanGenerator.toIban(accountNumber) }
    fun generateSpaydString(
        accountNumber: String,
        amount: Double,
        vs: String?,
        message: String?
    ): String {
        val iban = CzechIbanGenerator.toIban(accountNumber)
        return SpaydGenerator.generate(iban, amount, vs, message)
    }

    fun generateReservationPaymentSvg(reservation: Reservation): String {
        return generateQrSvg(SpaydGenerator.generate(iban, reservation.totalPrice, reservation.variableSymbol, null))
    }

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