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
    val contact: String
    val myReservations: String

    // Dashboard & Navigation
    val schedule: String
    val catalog: String
    val listView: String
    val calendarView: String

    // Headers & Sections
    val allEvents: String
    val openCourses: String
    val upcomingEvents: String
    val individualEvents: String

    // Feedback & Empty states
    val noEvents: String
    val noEventsFoundForFilter: String
    val calendarUnderConstruction: String

    // Filter logic
    val filter: String
    val clearFilterTooltip: String

    // Card Labels (Series/Event)
    val course: String
    val courseLessons: String
    val courseSignUp: String
    val partOfCourse: String
    val showDates: String
    val detail: String
    val priceLabel: String
    val maxCapacity: String

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