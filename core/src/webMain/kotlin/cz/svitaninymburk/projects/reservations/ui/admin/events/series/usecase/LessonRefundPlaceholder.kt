package cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase

import kotlin.math.floor

/**
 * Kolik vrátí kurz za jednu lekci a jedno místo, když admin sazbu nevyplní —
 * náhled do prázdného pole. Stejný výpočet jako `lessonShareOf` na serveru
 * (cena ÷ počet lekcí, dolů na koruny), jen bez příplatků z vlastních polí:
 * ty se liší rezervace od rezervace. null = z formuláře to zatím nejde spočítat.
 */
fun proportionalLessonRefundPreview(price: Number?, lessonCount: Int): Int? {
    val p = price?.toDouble() ?: return null
    if (p <= 0.0 || lessonCount <= 0) return null
    return floor(p / lessonCount).toInt()
}
