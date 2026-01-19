package cz.svitaninymburk.projects.reservations.i18n

import androidx.compose.runtime.mutableStateOf
import cz.svitaninymburk.projects.reservations.i18n.cs.CsStrings
import cz.svitaninymburk.projects.reservations.i18n.en.EnStrings


val strings = mutableStateOf<AppStrings>(EnStrings)

fun setLanguage(lang: String) {
    strings.value = when(lang) {
        "cs" -> CsStrings
        else -> EnStrings
    }
}

fun detectLanguage(navigatorLanguage: String?) {
    when (navigatorLanguage) {
        "cs" -> setLanguage("cs")
        "en" -> setLanguage("en")
        else -> setLanguage("en")
    }
}

interface AppStrings {
    val appName: String
    val logIn: String
    val reserve: String
    val dashboard: String
    val listView: String
    val calendarView: String
    val noEvents: String
    val currentMonth: String
    val contact: String

    val course: String
    val courseLessons: String
    val courseSignUp: String

    // Form labels & hints
    val nameLabel: String
    val nameHint: String
    val surnameLabel: String
    val surnameHint: String
    val emailLabel: String
    val emailHint: String
    val phoneLabel: String
    val phoneHint: String
    val seatCountLabel: String
    val seatCountHint: String

    // Helper functions for Calendar
    fun monthName(index: Int): String
    fun dayName(index: Int): String
    fun shortDayName(index: Int): String
}