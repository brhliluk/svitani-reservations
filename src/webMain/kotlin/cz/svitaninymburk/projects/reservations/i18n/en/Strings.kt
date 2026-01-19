package cz.svitaninymburk.projects.reservations.i18n.en

import cz.svitaninymburk.projects.reservations.i18n.AppStrings


object EnStrings : AppStrings {
    override val appName = "Reservations"
    override val logIn = "Log in"
    override val reserve = "Reserve"
    override val dashboard = "Dashboard"
    override val listView = "List-view"
    override val calendarView = "Calendar-view"
    override val noEvents = "No events"
    override val currentMonth = "Current month"
    override val contact = "Contact"
    override val myReservations = "My reservations"

    override val course = "Course"
    override val courseLessons = "lessons"
    override val courseSignUp = "Sign up"

    override val nameLabel = "First name"
    override val nameHint = "John"
    override val surnameLabel = "Last name"
    override val surnameHint = "Doe"
    override val emailLabel = "Email"
    override val emailHint = "john.doe@google.com"
    override val phoneLabel = "Phone"
    override val phoneHint = "+420 123 456 789"
    override val seatCountLabel = "Number of seats"
    override val seatCountHint = "1"

    // Lists for helper functions
    private val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    private val days = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )
    private val shortDays = listOf(
        "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
    )

    override fun monthName(index: Int) = months.getOrElse(index) { "" }
    override fun dayName(index: Int) = days.getOrElse(index) { "" }
    override fun shortDayName(index: Int) = shortDays.getOrElse(index) { "" }
}