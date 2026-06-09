package cz.svitaninymburk.projects.reservations.android.feature.reservations.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter

internal val dateFormatter = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm")
internal fun LocalDateTime.formatted() = toJavaLocalDateTime().format(dateFormatter)
