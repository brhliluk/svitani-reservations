package cz.svitaninymburk.projects.reservations.ui.util

import kotlin.math.ceil

/**
 * Stránkování používají všechny admin přehledy stejně, takže tyhle dva helpery
 * nepatří k žádné konkrétní obrazovce — dřív žily v `admin/events/usecase` a
 * rozvrh i rezervace si je odtud musely tahat napříč balíčky.
 */
fun pageCount(totalItems: Long, pageSize: Int): Int =
    maxOf(1, ceil(totalItems.toDouble() / pageSize).toInt())

fun <T> pageSlice(items: List<T>, page: Int, pageSize: Int): List<T> =
    items.drop(page * pageSize).take(pageSize)
