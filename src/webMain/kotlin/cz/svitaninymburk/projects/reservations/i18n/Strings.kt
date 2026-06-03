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

interface AppStrings : ErrorStrings {
    val locale: String
    val appName: String
    val logIn: String
    val reserve: String
    val dashboard: String
    val contact: String
    val myReservations: String
    val wallet: String
    val noUpcomingReservations: String

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
    val phoneHintAlt: String
    val seatCountLabel: String
    val seatCountHint: String
    val seatCountMaxReached: (Int) -> String
    val ownerEmailsLabel: String
    val ownerEmailPlaceholder: String
    val addOwnerEmailButton: String

    // Reservation Detail
    val eventNoLongerAvailable: String
    val reservationSummary: String
    val status: String
    val name: String
    val totalPrice: String
    val cancelReservation: String
    val cancelReservationConfirmBody: String
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

    // Reservation errors
    val reservationFailed: (String) -> String

    // Auth messages
    val registrationSuccess: String
    val forgotPasswordEmailSent: String

    // Auth dialog labels
    val loginTitle: String
    val passwordLabel: String
    val forgotPasswordLink: String
    val noAccountYet: String
    val signUpLink: String
    val registerTitle: String
    val registerSubtitle: String
    val passwordConfirmLabel: String
    val passwordMinLengthNote: String
    val passwordMismatchError: String
    val createAccountButton: String
    val alreadyHaveAccount: String
    val signInLink: String
    val forgotPasswordTitle: String
    val forgotPasswordSubtitle: String
    val sendInstructionsButton: String
    val backToLoginLink: String

    // Accessibility labels
    val ariaOpenMenu: String
    val ariaCapacityProgress: (Int, Int) -> String

    // Reservation Modal
    val reservationFor: (String) -> String
    val formTotalPrice: String
    val free: String
    val currency: String
    val persons: String
    val moreDetails: String
    val requiredFieldLegend: String
    val paymentType: String
    val bankTransfer: String
    val onSite: String
    val cancel: String
    val close: String
    val retry: String
    val timeRangeHint: (String, String) -> String
    val timeRangeError: String

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
    val viewAsUser: String
    val adminPanelLink: String

    // Admin Payments Screen
    val navPayments: String
    val allPayments: String
    val paymentsSubtitle: String
    val tableHeaderProcessedAt: String
    val tableHeaderContactName: String
    val tableHeaderAmount: String
    val tableHeaderPaymentType: String
    val tableHeaderPaymentSource: String
    val paymentSourceAutoFio: String
    val paymentSourceManualAdmin: String
    val paymentTypeBankTransfer: String
    val paymentTypeCash: String
    val paymentTypeFree: String
    val noPayments: String
    val paginationPrevious: String
    val paginationNext: String
    val paginationPageOf: (Int, Int) -> String

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
    val showAttendeeCount: String
    val showAttendeeCountHint: String

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
    val fieldTimeMultiplierToggle: String
    val fieldPriceModifierEnabled: String
    val fieldFlatFeeLabel: String
    val fieldFlatFeeFormula: String
    val fieldPerUnitPriceLabel: String
    val fieldPerUnitFormula: String
    val validationNameRequired: String
    val validationOwnerEmailRequired: String
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
    val lessonDayLabel: String
    val lessonTimeLabel: String
    val lessonDayPlaceholder: String
    val lessonSchedule: String
    fun lessonScheduleText(dayName: String, startTime: String, endTime: String): String
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

    // Unified Event Create Form (/admin/events/new)
    val newEventTitle: String
    val newEventSubtitle: String
    val eventTypeHeading: String
    val eventTypeSingle: String
    val eventTypeRecurring: String
    val eventTypeCourse: String
    val createEventButton: String
    val toastEventCreated: String
    val toastEventsCreated: (Int) -> String
    val toastCourseCreated: String
    val validationTitleRequired: String
    val validationDateRequired: String
    val validationDatesOrTimeRequired: String
    val validationCourseDatesRequired: String

    // Event Create Choose
    val chooseTypeTitle: String
    val chooseTypeSubtitle: String
    val chooseTypeSubtitleWith: (String) -> String
    val instanceCardTitle: String
    val instanceCardDescription: String
    val seriesCardTitle: String
    val seriesCardDescription: String

    // Series lesson list
    val seriesLessonsHeading: String
    val noLessonsYet: String
    val tableHeaderDate: String
    val tableHeaderTime: String
    fun lessonPreviewHeading(count: Int): String
    val lessonDateEditHint: String
    val lessonIndividualLabel: String
    val lessonIndividualTooltip: String
    val lessonIndividualBulkTooltip: String
    val lessonActiveBadge: String
    val lessonCancelledBadge: String
    val cancelLessonModalTitle: String
    fun cancelLessonModalBody(date: String): String
    val toastLessonCancelled: String
    val cancelLessonButton: String
    val lessonOptOut: String
    val lessonOptOutConfirmTitle: String
    val lessonOptOutConfirmBody: (date: String) -> String
    val lessonOptedOut: String
    val lessonOptOutLate: String
    val toastLessonOptOut: String

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
    val paymentMethodWallet: String
    val paymentMethodBankTransferAndWallet: String
    val paymentMethodCashAndWallet: String
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

    // On-site payment detail
    val onSitePaymentInfo: String
    val paymentProcessingNote: String

    // Admin Users Screen
    val allUsers: String
    val usersSubtitle: String
    val usersSearchPlaceholder: String
    val tableHeaderAuthType: String
    val tableHeaderReservations: String
    val noUsers: String
    val noUsersForSearch: (String) -> String
    val authTypeEmail: String
    val authTypeGoogle: String
    val tooltipChangeRole: String
    val tooltipDeleteUser: String
    val modalChangeRoleTitle: String
    val modalChangeRoleMsgPre: String
    val modalChangeRoleMsgMid: String
    val modalChangeRoleMsgPost: String
    val modalDeleteUserTitle: String
    val modalDeleteUserMsgPre: String
    val modalDeleteUserMsgPost: String
    val toastRoleChanged: (String) -> String
    val toastUserDeleted: (String) -> String
    val roleAdmin: String
    val roleUser: String

    // Attendance Screen
    val attendanceButton: String
    val attendancePrintHeader: (String) -> String
    val printList: String
    val emptyRowsLabel: String
    val tableHeaderPresence: String

    // Reservation detail extras
    val createdAt: String
    val showDetails: String
    val hideDetails: String
    val yes: String
    val no: String

    // Admin Edit & Delete
    val editTemplate: String
    val editEvent: String
    val editSeries: String
    val deleteTemplate: String
    val deleteEventLabel: String
    val deleteSeriesLabel: String
    val confirmDeleteTitle: String
    val deleteDefinitionImpact: (childCount: Int, reservationCount: Int) -> String
    val deleteEventImpact: (reservationCount: Int) -> String
    val propagateToChildren: String
    val propagateToChildrenNote: String
    val capacityWarningTitle: String
    val capacityWarningBody: String
    val saveChanges: String
    val toastEventUpdated: String
    val toastSeriesUpdated: String
    val toastDefinitionUpdated: String
    val toastEventDeleted: String
    val toastSeriesDeleted: String
    val toastDefinitionDeleted: String
    val editTemplateTitle: String
    val editInstanceTitle: String
    val editSeriesTitle: String

    // Helper functions for Calendar
    fun monthName(index: Int): String
    fun dayName(index: Int): String
    fun shortDayName(index: Int): String

    // Admin Settings Screen
    val navSettings: String
    val settingsTitle: String
    val settingsEmailCard: String
    val settingsPaymentCard: String
    val settingsBankAccount: String
    val settingsFioToken: String
    val settingsSenderEmail: String
    val settingsGmailPassword: String
    val settingsSenderDisplayName: String
    val settingsChangeButton: String
    val settingsTestEmailButton: String
    val settingsTestFioButton: String
    val settingsSaveButton: String
    val settingsTestPassed: String
    fun settingsTestFailed(reason: String): String
    val settingsTestRequiredBeforeSave: String
    val settingsSavedSuccess: String
    val settingsGmailPasswordHint: String
    val settingsFioTokenHint: String

    // Wallet System
    val walletHasCode: String
    val walletCode: String
    val walletCodePlaceholder: String
    val walletCodeHint: String
    val walletAutoCreate: String
    val walletUseHint: String
    val walletBalance: String
    val walletEmailMismatchWarning: String
    val walletEmailMismatchConfirm: String
    val walletCreditApplied: String
    val remainingToPay: String
    val walletCreditIssued: String
    val adminWallets: String
    val adminWalletDetail: String
    val adminWalletAdjust: String
    val adminWalletAdjustNote: String
    val adminWalletCreditButton: String
    val adminWalletDebitButton: String
    val lessonRefundAmount: String
    val walletExpiresOn: String

    // Wallet lookup page
    val walletLookupTitle: String
    val walletLookupSubtitle: String
    val walletLookupEmailLabel: String
    val walletLookupSubmit: String
    val walletLookupNotFound: String

    // Cancellation policy box
    val cancellationPolicyTitle: String
    val cancellationPolicyDeadline: String
    val cancellationPolicyLessonOptOut: String
    val cancellationPolicyNoRefundUnpaid: String
    val cancellationPolicyNoRefundLate: String
    val cancellationPolicyWalletExplain: String

    // Dynamic refund preview in cancellation dialog
    val cancellationRefundEligible: (String) -> String
    val cancellationLessonRefundEligible: String
    val cancellationNoRefund: String
    val cancellationWindowPassed: String
    val cancellationNotPaid: String

    // Privacy Policy
    val privacyTitle: String
    val privacyControllerHeading: String
    val privacyControllerBody: String
    val privacyDataHeading: String
    val privacyDataBody: String
    val privacyPurposeHeading: String
    val privacyPurposeBody: String
    val privacyRecipientsHeading: String
    val privacyRecipientsBody: String
    val privacyRetentionHeading: String
    val privacyRetentionBody: String
    val privacyRightsHeading: String
    val privacyRightsBody: String
    val privacyContactHeading: String
    val privacyContactBody: String
    val privacyPolicyLink: String
    val registrationPrivacyNote: String
}