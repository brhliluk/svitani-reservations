package cz.svitaninymburk.projects.reservations.i18n

import androidx.compose.runtime.mutableStateOf
import cz.svitaninymburk.projects.reservations.i18n.cs.CsStrings
import cz.svitaninymburk.projects.reservations.i18n.en.EnStrings
import web.navigator.navigator
import kotlin.js.toList


private val supportedLanguages = mapOf(
    "cs" to CsStrings,
    "en" to EnStrings
)

private fun resolveStrings(): AppStrings {
    val languages = navigator.languages.toList().map { it.toString().substringBefore('-') }
    return supportedLanguages.firstNotNullOfOrNull { (key, value) -> languages.find { it == key }?.let { value } } ?: CsStrings
}

val strings = mutableStateOf(resolveStrings())

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
    val backToDashboard: String

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
    val filterIsActive: (String) -> String

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

    // Reservation Detail
    val reservationSummary: String
    val status: String
    val name: String
    val totalPrice: String
    val cancelReservation: String
    val qrPayment: String
    val shareOrDownload: String
    val accountNumber: String
    val variableSymbol: String
    val reservationCancelledMessage: String
    val reservationPaidMessage: String
    val copied: String

    // Reservation Statuses
    val reservationCreated: String
    val waitingForPayment: String
    val unpaid: String
    val reservationConfirmed: String
    val everythingIsOK: String
    val paid: String
    val reservationCancelled: String
    val reservationNotValid: String
    val cancelled: String
    val reservationRejected: String
    val reservationNotApproved: String
    val rejected: String
    val copyright: String

    // Reservation Modal
    val reservationFor: (String) -> String
    val formTotalPrice: String
    val free: String
    val currency: String
    val persons: String
    val moreDetails: String
    val paymentType: String
    val bankTransfer: String
    val onSite: String
    val cancel: String
    val close: String

    // Calendar
    val more: (Int) -> String

    // Admin Dashboard
    val dashboardWelcome: String
    val dashboardTodayParticipants: String
    val dashboardPendingPayment: String
    val dashboardPendingPaymentsDesc: (Int) -> String
    val dashboardFreeSpots: String
    val dashboardFreeSpotsDesc: String
    val dashboardUpcomingEvents: String
    val dashboardNoUpcomingEvents: String
    val dashboardPendingReservations: String
    val dashboardAllPaid: String

    // Admin Reservations Screen
    val allReservations: String
    val reservationsSubtitle: String
    val search: String
    val searchPlaceholder: String
    val clearSearch: String
    val tableHeaderEvent: String
    val noReservationsForSearch: (String) -> String
    val noReservations: String

    // Admin Layout / Sidebar
    val adminPanel: String
    val adminNavTitle: String
    val navEvents: String
    val navReservations: String
    val navUsers: String
    val logOut: String

    // Admin Events Screen
    val adminEventsSubtitle: String
    val createNew: String
    val emptyTemplates: String
    val noDates: String
    val datesCount: (Int) -> String
    val addDate: String
    val adminCourse: String
    val noInstancesMessage: String
    val badgeOneTime: String
    val capacityFull: String
    val showLess: String
    val showMore: (Int) -> String
    val loadingError: (String) -> String

    // Shared Admin Form Elements
    val descriptionLabel: String
    val basicInfoHeading: String
    val defaultPriceLabel: String
    val capacityPersonLabel: String
    val defaultDurationLabel: String
    val durationLabel: String
    val hours: String
    val minutes: String
    val allowedPaymentsLabel: String
    val paymentOnSite: String
    val templateSelectionLabel: String
    val templatePlaceholder: String
    val noTemplatesHeading: String
    val createTemplateButton: String
    val errorToast: (String) -> String
    val toastTemplatesLoadError: (String) -> String

    // Event Definition Form
    val eventNameLabel: String
    val newTemplateTitle: String
    val newTemplateSubtitle: String
    val recurrenceHeading: String
    val recurrenceTypeLabel: String
    val recurrenceNone: String
    val recurrenceDaily: String
    val recurrenceWeekly: String
    val recurrenceMonthly: String
    val recurrenceEndLabel: String
    val customFieldsHeading: String
    val addFieldButton: String
    val addTextField: String
    val addNumberField: String
    val addBooleanField: String
    val addTimeRangeField: String
    val noCustomFieldsMessage: String
    val fieldKeyLabel: String
    val fieldLabelLabel: String
    val fieldRequired: String
    val fieldTypeText: String
    val fieldTypeNumber: String
    val fieldTypeBoolean: String
    val fieldTypeTimeRange: String
    val validationNameRequired: String
    val validationRecurrenceEndRequired: String
    val validationRecurrenceDateFormat: String
    val templateSavedToast: String

    // Event Instance Form
    val newInstanceTitle: String
    val newInstanceSubtitle: String
    val noTemplatesInstanceMessage: String
    val dateLabelField: String
    val timeLabelField: String
    val overrideHeading: String
    val overrideDescription: String
    val instanceTitleLabel: String
    val recurrencePreviewHeading: (Int) -> String
    val recurrencePreviewError: String
    val createInstanceButton: String
    val createInstancesButton: (Int) -> String
    val validationDateTimeRequired: String
    val validationDateTimeFormat: String
    val validationNoDates: String
    val toastInstanceCreateError: (String, String) -> String
    val toastInstancesCreated: (Int) -> String
    val toastInstanceCreated: String

    // Event Series Form
    val newSeriesTitle: String
    val newSeriesSubtitle: String
    val noTemplatesSeriesMessage: String
    val startDateLabel: String
    val endDateLabel: String
    val lessonCountLabel: String
    val autoFillAlert: String
    val seriesOverrideHeading: String
    val seriesOverrideDescription: String
    val seriesTitleLabel: String
    val fullCoursePriceLabel: String
    val createSeriesButton: String
    val validationDatesRequired: String
    val validationSeriesTitleRequired: String
    val validationStartDateFormat: String
    val validationEndDateFormat: String
    val validationEndBeforeStart: String
    val toastSeriesCreated: String

    // Event Create Choose
    val chooseTypeTitle: String
    val chooseTypeSubtitle: String
    val chooseTypeSubtitleWith: (String) -> String
    val instanceCardTitle: String
    val instanceCardDescription: String
    val seriesCardTitle: String
    val seriesCardDescription: String

    // Event Detail
    val occupancyStatTitle: String
    val capacityFilled: String
    val spotsRemaining: (Int) -> String
    val revenueStatTitle: String
    val revenueStatDesc: String
    val tableHeaderParticipant: String
    val tableHeaderSeats: String
    val tableHeaderPaymentStatus: String
    val tableHeaderActions: String
    val noParticipants: String
    val statusOnSiteBadge: String
    val statusWaiting: String
    val paymentMethodCash: String
    val tooltipAcceptCash: String
    val tooltipMarkPaid: String
    val buttonCollect: String
    val tooltipCancelReservation: String
    val modalConfirmPaymentTitle: String
    val modalCancelReservationTitle: String
    val modalConfirmPaymentMsgPre: String
    val modalConfirmPaymentMsgPost: String
    val modalCancelMsgPre: String
    val modalCancelMsgPost: String
    val modalBack: String
    val modalConfirmAction: String
    val modalConfirmCancelAction: String
    val toastPaymentConfirmed: (String) -> String
    val toastReservationCancelled: (String) -> String
    val invalidEventId: String

    // Attendance Screen
    val attendanceButton: String
    val attendancePrintHeader: (String) -> String
    val printList: String
    val emptyRowsLabel: String
    val tableHeaderPresence: String

    // Helper functions for Calendar
    fun monthName(index: Int): String
    fun dayName(index: Int): String
    fun shortDayName(index: Int): String
}