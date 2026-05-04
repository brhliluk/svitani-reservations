package cz.svitaninymburk.projects.reservations.i18n.en

import cz.svitaninymburk.projects.reservations.i18n.AppStrings


object EnStrings : AppStrings {
    override val locale = "en"
    override val appName = "Reservations"
    override val logIn = "Log in"
    override val reserve = "Reserve"
    override val dashboard = "Dashboard"
    override val contact = "Contact"
    override val myReservations = "My reservations"
    override val noUpcomingReservations = "You have no upcoming reservations."

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

    // Reservation errors
    override val reservationFailed: (String) -> String = { "Reservation failed: $it" }

    // Auth messages
    override val registrationSuccess = "Registration successful! We sent a confirmation to your email."
    override val forgotPasswordEmailSent = "Password reset instructions were sent to your email."

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

    // Admin Dashboard
    override val dashboardWelcome = "Welcome back! Here's how your reservations look right now."
    override val dashboardTodayParticipants = "Today's participants"
    override val dashboardPendingPayment = "Awaiting payment"
    override val dashboardPendingPaymentsDesc: (Int) -> String = { "Total $it reservations" }
    override val dashboardFreeSpots = "Free spots"
    override val dashboardFreeSpotsDesc = "In events this week"
    override val dashboardUpcomingEvents = "Upcoming events"
    override val dashboardNoUpcomingEvents = "No upcoming events."
    override val dashboardPendingReservations = "Recent unpaid reservations"
    override val dashboardAllPaid = "All reservations are paid!"

    // Admin Reservations Screen
    override val allReservations = "All reservations"
    override val reservationsSubtitle = "Overview and management of all registrations"
    override val search = "Search"
    override val searchPlaceholder = "Search name, email or variable symbol..."
    override val clearSearch = "Clear search"
    override val tableHeaderEvent = "Event / Course"
    override val noReservationsForSearch: (String) -> String = { "No reservations found for '$it'." }
    override val noReservations = "No reservations yet."

    // Admin Layout / Sidebar
    override val adminPanel = "Admin Panel"
    override val adminNavTitle = "Administration"
    override val navEvents = "Events & Courses"
    override val navReservations = "Reservations"
    override val navUsers = "Users"
    override val logOut = "Log out"

    // Admin Events Screen
    override val adminEventsSubtitle = "Manage catalog and open new dates"
    override val createNew = "Create new"
    override val emptyTemplates = "Nothing here yet. Create your first template!"
    override val noDates = "No dates"
    override val datesCount: (Int) -> String = { "$it dates" }
    override val addDate = "Date"
    override val adminCourse = "Course"
    override val noInstancesMessage = "No dates yet. Create the first by clicking + Date or + Course."
    override val badgeOneTime = "One-time"
    override val capacityFull = "FULL"
    override val showLess = "Show less"
    override val showMore: (Int) -> String = { "Show $it more" }
    override val loadingError: (String) -> String = { "Loading error: $it" }

    // Shared Admin Form Elements
    override val descriptionLabel = "Description"
    override val basicInfoHeading = "Basic info"
    override val defaultPriceLabel = "Default price"
    override val capacityPersonLabel = "Person capacity"
    override val defaultDurationLabel = "Default duration"
    override val durationLabel = "Duration"
    override val hours = "h"
    override val minutes = "min"
    override val allowedPaymentsLabel = "Allowed payments"
    override val paymentOnSite = "On-site"
    override val templateSelectionLabel = "Which template are we basing this on?"
    override val templatePlaceholder = "-- Select a template --"
    override val noTemplatesHeading = "No templates"
    override val createTemplateButton = "Create template"
    override val errorToast: (String) -> String = { "Error: $it" }
    override val toastTemplatesLoadError: (String) -> String = { "Error loading templates: $it" }

    // Event Definition Form
    override val eventNameLabel = "Name (e.g. Yoga for Beginners)"
    override val newTemplateTitle = "New event template"
    override val newTemplateSubtitle = "Create a base definition to schedule dates from."
    override val recurrenceHeading = "Recurrence"
    override val recurrenceTypeLabel = "Recurrence type"
    override val recurrenceNone = "None"
    override val recurrenceDaily = "Daily"
    override val recurrenceWeekly = "Weekly"
    override val recurrenceMonthly = "Monthly"
    override val recurrenceEndLabel = "Repeat until"
    override val customFieldsHeading = "Custom reservation form fields"
    override val addFieldButton = "Add field"
    override val addTextField = "Text field"
    override val addNumberField = "Number field"
    override val addBooleanField = "Checkbox (Yes/No)"
    override val addTimeRangeField = "Time range"
    override val noCustomFieldsMessage = "No custom fields yet. People will only fill in Name, Email, and Phone."
    override val fieldKeyLabel = "Key (system)"
    override val fieldLabelLabel = "Display text (Label)"
    override val fieldRequired = "Required field"
    override val fieldTypeText = "Type: Text"
    override val fieldTypeNumber = "Type: Number"
    override val fieldTypeBoolean = "Type: Yes/No"
    override val fieldTypeTimeRange = "Type: Time range"
    override val validationNameRequired = "Name is required"
    override val validationRecurrenceEndRequired = "Enter the recurrence end date."
    override val validationRecurrenceDateFormat = "Invalid recurrence end date format."
    override val templateSavedToast = "Template saved successfully!"

    // Event Instance Form
    override val newInstanceTitle = "Schedule new date"
    override val newInstanceSubtitle = "Select a template, set date and time. You can adjust the details for this date."
    override val noTemplatesInstanceMessage = "Before scheduling a date, create at least one event template."
    override val dateLabelField = "Event date"
    override val timeLabelField = "Start time"
    override val overrideHeading = "Edits for this specific date"
    override val overrideDescription = "These fields are prefilled from the template. Changes apply only to this one date."
    override val instanceTitleLabel = "Date title"
    override val recurrencePreviewHeading: (Int) -> String = { "Date preview ($it)" }
    override val recurrencePreviewError = "End date is before start date. No dates will be created."
    override val createInstanceButton = "Schedule date"
    override val createInstancesButton: (Int) -> String = { "Schedule $it dates" }
    override val validationDateTimeRequired = "You must select a date and time."
    override val validationDateTimeFormat = "Invalid date or time format."
    override val validationNoDates = "No dates to create."
    override val toastInstanceCreateError: (String, String) -> String = { dt, err -> "Error creating date $dt: $err" }
    override val toastInstancesCreated: (Int) -> String = { "Created $it dates!" }
    override val toastInstanceCreated = "Date was successfully scheduled!"

    // Event Series Form
    override val newSeriesTitle = "Create course"
    override val newSeriesSubtitle = "Set the period and number of lessons. Details are prefilled from the template."
    override val noTemplatesSeriesMessage = "Before creating a course, create an event template first."
    override val startDateLabel = "Start date"
    override val endDateLabel = "End date"
    override val lessonCountLabel = "Number of lessons"
    override val lessonDayLabel = "Lesson day (optional)"
    override val lessonTimeLabel = "Lesson start time"
    override val lessonDayPlaceholder = "— Not selected —"
    override val lessonSchedule = "Schedule"
    override fun lessonScheduleText(dayName: String, startTime: String, endTime: String) =
        "every $dayName $startTime–$endTime"
    override val autoFillAlert = "End date and lesson count were prefilled from the template. You can edit them."
    override val seriesOverrideHeading = "Edits for this course"
    override val seriesOverrideDescription = "Prefilled from template. Changes apply only to this course."
    override val seriesTitleLabel = "Course name"
    override val fullCoursePriceLabel = "Price (full course)"
    override val createSeriesButton = "Create course"
    override val validationDatesRequired = "You must fill in start and end date."
    override val validationSeriesTitleRequired = "Course name is required."
    override val validationStartDateFormat = "Invalid start date format."
    override val validationEndDateFormat = "Invalid end date format."
    override val validationEndBeforeStart = "End date must be after start date."
    override val toastSeriesCreated = "Course was successfully created!"

    // Unified Event Create Form
    override val newEventTitle = "New event"
    override val newEventSubtitle = "Create a one-time, recurring event or a course"
    override val eventTypeHeading = "Event type"
    override val eventTypeSingle = "One-time"
    override val eventTypeRecurring = "Recurring"
    override val eventTypeCourse = "Course"
    override val createEventButton = "Create"
    override val toastEventCreated = "Event was successfully created!"
    override val toastEventsCreated: (Int) -> String = { "Created $it events!" }
    override val toastCourseCreated = "Course was successfully created!"
    override val validationTitleRequired = "Event name is required."
    override val validationDateRequired = "You must select an event date."
    override val validationDatesOrTimeRequired = "You must fill in date and time."
    override val validationCourseDatesRequired = "You must fill in start date, end date and lesson count."

    // Event Create Choose
    override val chooseTypeTitle = "How do you want to schedule this template?"
    override val chooseTypeSubtitle = "Select date type"
    override val chooseTypeSubtitleWith: (String) -> String = { "Template: $it" }
    override val instanceCardTitle = "One-time date"
    override val instanceCardDescription = "One-time event with a specific date and time."
    override val seriesCardTitle = "Course / multiple lessons"
    override val seriesCardDescription = "Recurring course with multiple lessons in a given period."

    // Event Detail
    override val occupancyStatTitle = "Occupancy"
    override val capacityFilled = "Capacity filled"
    override val spotsRemaining: (Int) -> String = { "$it spots remaining" }
    override val revenueStatTitle = "Collected (Confirmed)"
    override val revenueStatDesc = "Total from paid"
    override val tableHeaderParticipant = "Participant"
    override val tableHeaderSeats = "Seats"
    override val tableHeaderPaymentStatus = "Payment status"
    override val tableHeaderActions = "Actions"
    override val noParticipants = "No registered participants yet."
    override val statusOnSiteBadge = "On-site"
    override val statusWaiting = "Waiting"
    override val paymentMethodCash = "Cash"
    override val tooltipAcceptCash = "Accept cash"
    override val tooltipMarkPaid = "Mark as paid"
    override val buttonCollect = "Collect"
    override val tooltipCancelReservation = "Cancel reservation"
    override val modalConfirmPaymentTitle = "Confirm payment"
    override val modalCancelReservationTitle = "Cancel reservation"
    override val modalConfirmPaymentMsgPre = "Do you really want to mark the reservation for participant "
    override val modalConfirmPaymentMsgPost = " as paid?"
    override val modalCancelMsgPre = "Do you really want to cancel the reservation for participant "
    override val modalCancelMsgPost = "? This action is irreversible."
    override val modalBack = "Back"
    override val modalConfirmAction = "Yes, confirm"
    override val modalConfirmCancelAction = "Yes, cancel"
    override val toastPaymentConfirmed: (String) -> String = { "Payment from $it confirmed!" }
    override val toastReservationCancelled: (String) -> String = { "Reservation from $it was cancelled!" }
    override val invalidEventId = "Invalid event ID."

    // On-site payment detail
    override val onSitePaymentInfo = "Please pay cash on site."

    // Admin Users Screen
    override val allUsers = "Users"
    override val usersSubtitle = "Overview and management of user accounts"
    override val usersSearchPlaceholder = "Search by name or email..."
    override val tableHeaderAuthType = "Auth type"
    override val tableHeaderReservations = "Reservations"
    override val noUsers = "No users yet."
    override val noUsersForSearch: (String) -> String = { "No users found for '$it'." }
    override val authTypeEmail = "Email"
    override val authTypeGoogle = "Google"
    override val tooltipChangeRole = "Change role"
    override val tooltipDeleteUser = "Delete user"
    override val modalChangeRoleTitle = "Change role"
    override val modalChangeRoleMsgPre = "Do you really want to change the role of "
    override val modalChangeRoleMsgMid = " to "
    override val modalChangeRoleMsgPost = "?"
    override val modalDeleteUserTitle = "Delete user"
    override val modalDeleteUserMsgPre = "Do you really want to delete the account of "
    override val modalDeleteUserMsgPost = "? This action is irreversible."
    override val toastRoleChanged: (String) -> String = { "Role of $it was changed." }
    override val toastUserDeleted: (String) -> String = { "Account of $it was deleted." }
    override val roleAdmin = "Administrator"
    override val roleUser = "User"

    // Attendance Screen
    override val attendanceButton = "Attendance"
    override val attendancePrintHeader: (String) -> String = { "Attendance: $it" }
    override val printList = "Print"
    override val emptyRowsLabel = "Extra blank rows:"
    override val tableHeaderPresence = "Presence / Signature"

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

    // Reservation detail extras
    override val createdAt = "Created"
    override val showDetails = "Show details"
    override val hideDetails = "Hide details"
    override val yes = "Yes"
    override val no = "No"

    // Admin Edit & Delete
    override val editTemplate = "Edit template"
    override val editEvent = "Edit event"
    override val editSeries = "Edit course"
    override val deleteTemplate = "Delete template"
    override val deleteEventLabel = "Delete event"
    override val deleteSeriesLabel = "Delete course"
    override val confirmDeleteTitle = "Are you sure?"
    override val deleteDefinitionImpact: (Int, Int) -> String = { children, reservations ->
        "This will delete $children instances/courses and cancel $reservations active reservations. This cannot be undone."
    }
    override val deleteEventImpact: (Int) -> String = { reservations ->
        if (reservations > 0) "This will cancel $reservations active reservations. This cannot be undone."
        else "This cannot be undone."
    }
    override val propagateToChildren = "Apply changes to all instances and courses"
    override val propagateToChildrenNote = "Capacity will be applied to all instances and courses. If any have more reservations than the new capacity, overbooking may occur."
    override val capacityWarningTitle = "Capacity warning"
    override val capacityWarningBody = "New capacity is lower than the number of existing reservations. Save anyway?"
    override val saveChanges = "Save changes"
    override val toastEventUpdated = "Event updated successfully."
    override val toastSeriesUpdated = "Course updated successfully."
    override val toastDefinitionUpdated = "Template updated successfully."
    override val toastEventDeleted = "Event deleted."
    override val toastSeriesDeleted = "Course deleted."
    override val toastDefinitionDeleted = "Template and all its instances deleted."
    override val editTemplateTitle = "Edit template"
    override val editInstanceTitle = "Edit event"
    override val editSeriesTitle = "Edit course"

    // ErrorStrings
    override val errorInvalidCredentials = "Invalid credentials"
    override val errorUserAlreadyExists = "Account already exists"
    override val errorInvalidGoogleToken = "Invalid Google token"
    override val errorInvalidToken = "Invalid token"
    override val errorTokenExpired = "Token expired"
    override val errorUserNotFound = "User not found"
    override val errorProcessingRequest = "Error processing request"
    override val errorNotLoggedIn = "User not logged in"
    override fun errorLoggedInWithAnotherProvider(providerName: String?) = "Logged in with another method: $providerName"

    override val errorReservationNotFound = "Reservation not found"
    override val errorEventInstanceNotFound = "Event not found"
    override val errorEventSeriesNotFound = "Course not found"
    override val errorCapacityExceeded = "Event capacity exceeded"
    override val errorEventAlreadyFinished = "Event has already ended"
    override val errorEventAlreadyStarted = "Event has already started"
    override val errorEventCancelled = "Event was cancelled"
    override val errorFailedToGetReservations = "Failed to get reservations"
    override fun errorFailedToSendCancellationEmail(cause: String) = "Failed to send cancellation email: $cause"

    override fun errorEventInstanceNotFoundId(id: String) = "Event with id $id not found"
    override fun errorEventDefinitionNotFoundId(id: String) = "Event template with id $id not found"
    override val errorFailedToGetDefinitions = "Failed to get definitions"
    override val errorFailedToGetInstances = "Failed to get events"
    override val errorFailedToGetSeries = "Failed to get courses"

    override val errorAdminReservationNotFound = "Reservation not found"
    override fun errorWrongReservationState(stateName: String) = "Reservation state is not for payment, but: $stateName"
    override val errorAdminEventNotFound = "Event not found"
    override val errorAdminCourseNotFound = "Course not found"
    override val errorAdminUserNotFound = "User not found"
    override val errorAdminDefinitionNotFound = "Definition not found"
}