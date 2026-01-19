package cz.svitaninymburk.projects.reservations.i18n.en

import cz.svitaninymburk.projects.reservations.i18n.AppStrings


object EnStrings : AppStrings {
    override val appName = "Reservations"
    override val logIn = "Log in"
    override val reserve = "Reserve"
    override val dashboard = "Dashboard"
    override val contact = "Contact"
    override val myReservations = "My reservations"

    // Dashboard
    override val schedule = "Schedule"
    override val catalog = "Catalog"
    override val listView = "List"
    override val calendarView = "Calendar"

    // Headers
    override val allEvents = "All events"
    override val openCourses = "Open Courses"
    override val upcomingEvents = "Upcoming Events"
    override val individualEvents = "Individual Events"

    // States
    override val noEvents = "No events"
    override val noEventsFoundForFilter = "No events found for this selection."
    override val calendarUnderConstruction = "Calendar is under construction :)"

    // Filter
    override val filter = "Filter"
    override val clearFilterTooltip = "Click to clear filter"

    // Cards
    override val course = "Course"
    override val courseLessons = "lessons"
    override val courseSignUp = "Sign up"
    override val partOfCourse = "Part of course"
    override val showDates = "Dates"
    override val detail = "Detail"
    override val priceLabel = "Price"
    override val maxCapacity = "Max"

    // Forms
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