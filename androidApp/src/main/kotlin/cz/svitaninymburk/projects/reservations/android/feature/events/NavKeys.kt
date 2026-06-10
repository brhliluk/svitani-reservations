package cz.svitaninymburk.projects.reservations.android.feature.events

import androidx.navigation3.runtime.NavKey

data object EventList : NavKey
data class EventInstanceDetail(val id: String) : NavKey
data class EventSeriesDetail(val id: String) : NavKey
