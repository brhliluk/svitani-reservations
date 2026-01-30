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
    override val backToDashboard = "Back to dashboard"

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
    override val filterIsActive: (String) -> String = { "Filter: $it" }

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

    // Reservation Detail
    override val reservationSummary = "Reservation summary"
    override val status = "Status"
    override val name = "Name"
    override val totalPrice = "Total price"
    override val cancelReservation = "Cancel reservation"
    override val qrPayment = "QR payment"
    override val shareOrDownload = "Click to share / download"
    override val accountNumber = "Account number"
    override val variableSymbol = "VS"
    override val reservationCancelledMessage = "This reservation is cancelled."
    override val reservationPaidMessage = "Everything is paid. We look forward to seeing you!"
    override val copied = "Copied"

    // Reservation Statuses
    override val reservationCreated = "Reservation created"
    override val waitingForPayment = "Waiting for payment"
    override val unpaid = "Unpaid"
    override val reservationConfirmed = "Reservation confirmed"
    override val everythingIsOK = "Everything is in order"
    override val paid = "Paid"
    override val reservationCancelled = "Reservation cancelled"
    override val reservationNotValid = "This reservation is not valid"
    override val cancelled = "Cancelled"
    override val reservationRejected = "Reservation rejected"
    override val reservationNotApproved = "The organizer did not approve the reservation"
    override val rejected = "Rejected"
    override val copyright = "© 2024 Reservation System"

    // Reservation Modal
    override val reservationFor: (String) -> String = { "Reservation for: $it" }
    override val formTotalPrice = "Total price"
    override val free = "Free"
    override val currency = "CZK"
    override val persons = "persons"
    override val moreDetails = "More details"
    override val paymentType = "Payment type"
    override val bankTransfer = "Bank transfer"
    override val onSite = "On-site"
    override val cancel = "Cancel"
    override val close = "close"

    // Calendar
    override val more: (Int) -> String = { "+$it more" }

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