package cz.svitaninymburk.projects.reservations.i18n

interface ErrorStrings {
    // Auth errors
    val errorInvalidCredentials: String
    val errorUserAlreadyExists: String
    val errorInvalidGoogleToken: String
    val errorInvalidToken: String
    val errorTokenExpired: String
    val errorUserNotFound: String
    val errorProcessingRequest: String
    val errorNotLoggedIn: String
    fun errorLoggedInWithAnotherProvider(providerName: String?): String

    // Reservation errors
    val errorReservationNotFound: String
    val errorEventInstanceNotFound: String
    val errorEventSeriesNotFound: String
    val errorCapacityExceeded: String
    val errorEventAlreadyFinished: String
    val errorEventAlreadyStarted: String
    val errorEventCancelled: String
    val errorFailedToGetReservations: String
    fun errorFailedToSendCancellationEmail(cause: String): String

    // Event errors
    fun errorEventInstanceNotFoundId(id: String): String
    fun errorEventDefinitionNotFoundId(id: String): String
    val errorFailedToGetDefinitions: String
    val errorFailedToGetInstances: String
    val errorFailedToGetSeries: String

    // Admin errors
    val errorAdminReservationNotFound: String
    fun errorWrongReservationState(stateName: String): String
    val errorAdminEventNotFound: String
    val errorAdminCourseNotFound: String
    val errorAdminUserNotFound: String
}
