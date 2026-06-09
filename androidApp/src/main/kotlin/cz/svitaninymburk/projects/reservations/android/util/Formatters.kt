package cz.svitaninymburk.projects.reservations.android.util

internal fun Double.toCzkString() = if (this % 1.0 == 0.0) "${toLong()} Kč" else "%.2f Kč".format(this)
