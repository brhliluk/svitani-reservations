package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.i18n.ErrorStrings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * Čím se nová rezervace tluče s tou, kterou už člověk má. Rozhoduje jen o formulaci
 * hlášky — ve všech případech jde o varování, přes které se dá projít.
 */
@Serializable
enum class DuplicateScope {
    /** Rezervace přesně na tutéž akci nebo tentýž kurz. */
    SAME_EVENT,
    /** Rezervuje jednotlivou lekci, ale je přihlášený na celý kurz. */
    PARENT_SERIES,
    /** Rezervuje celý kurz, ale na některou jeho lekci už rezervaci má. */
    SERIES_LESSON,
}

@Serializable @SerialName("reservation") sealed interface ReservationError : AppError {
    @Serializable @SerialName("create") sealed interface CreateReservation : ReservationError
    @Serializable @SerialName("cancel") sealed interface CancelReservation : ReservationError
    @Serializable @SerialName("get_all") sealed interface GetAll : ReservationError
    @Serializable @SerialName("get") sealed interface Get : ReservationError
    @Serializable @SerialName("get_detail") sealed interface GetDetail: ReservationError
    @Serializable @SerialName("get_wallet") sealed interface GetWalletInfo : ReservationError
    @Serializable @SerialName("claim") sealed interface ClaimReservation : ReservationError

    /** Vyžádání potvrzovacího odkazu na rezervace vedené na e-mail účtu. */
    @Serializable @SerialName("request_claim") sealed interface RequestClaim : ReservationError

    /** Uplatnění toho odkazu. */
    @Serializable @SerialName("confirm_claim") sealed interface ConfirmClaim : ReservationError

    @Serializable data object ReservationNotFound : CreateReservation, CancelReservation, Get, GetDetail, ClaimReservation
    @Serializable data object EventInstanceNotFound : GetDetail
    @Serializable data object EventSeriesNotFound : GetDetail
    @Serializable data object EventAlreadyFinished : CreateReservation, CancelReservation
    @Serializable data object EventAlreadyStarted : CreateReservation, CancelReservation
    @Serializable data object EventCancelled : CreateReservation
    @Serializable data object CapacityExceeded : CreateReservation
    @Serializable data object MultipleSeatsNotAllowed : CreateReservation
    @Serializable data object InvalidSeatCount : CreateReservation
    @Serializable data object FailedToGetAllReservations : GetAll
    @Serializable data class SystemError(val message: String) : CreateReservation
    @Serializable data object NotASeriesReservation : CancelReservation
    @Serializable data object InstanceNotInSeries : CancelReservation
    @Serializable data object AlreadyOptedOut : CancelReservation

    /** Rezervace už je zrušená — druhé storno by vrátilo peníze podruhé. */
    @Serializable data object AlreadyCancelled : CancelReservation
    @Serializable data object WalletNotFound : CancelReservation, CreateReservation, GetWalletInfo
    @Serializable data object WalletEmpty : CreateReservation
    @Serializable data object WalletEmailMismatch : CancelReservation
    @Serializable data object ReservationDeadlinePassed : CreateReservation
    @Serializable data object EventNotFull : CreateReservation
    @Serializable data object WaitlistNotAvailable : CreateReservation
    @Serializable data object WaitlistFull : CreateReservation

    /** Rezervaci už někdo přivlastnil — i kdyby to byl někdo jiný, ven jde jen tohle. */
    @Serializable data object AlreadyClaimed : ClaimReservation
    /** Kontaktní e-mail rezervace není e-mail přihlášeného účtu. */
    @Serializable data object EmailDoesNotMatch : ClaimReservation
    /** Zrušená, zamítnutá nebo doběhlá rezervace — v „Moje rezervace“ by se stejně neukázala. */
    @Serializable data object NotClaimable : ClaimReservation

    /** Na e-mail účtu není co připsat. Dialog by v tu chvíli neměl být vidět. */
    @Serializable data object NothingToClaim : RequestClaim
    /** Odkaz se nepodařilo odeslat — bez mailu není co potvrzovat. */
    @Serializable data class ClaimEmailSendFailed(val message: String) : RequestClaim
    /** Neznámý odkaz, nebo už neplatí pro dnešní e-mail účtu. */
    @Serializable data object ClaimLinkInvalid : ConfirmClaim
    /** Odkaz je starší, než dokud platí. */
    @Serializable data object ClaimLinkExpired : ConfirmClaim

    /**
     * Na tuto akci už na zadaný e-mail rezervace existuje. Není to chyba, ale dotaz —
     * server nic nezaložil ani nezabral místo, takže se dá poslat znovu
     * s `acknowledgedDuplicate = true` (rezervuje se druhé dítě, kamarádka…).
     */
    @Serializable data class AlreadyReserved(val scope: DuplicateScope) : CreateReservation
}

fun ReservationError.localizedMessage(strings: ErrorStrings): String = when (this) {
    is ReservationError.ReservationNotFound -> strings.errorReservationNotFound
    is ReservationError.EventInstanceNotFound -> strings.errorEventInstanceNotFound
    is ReservationError.EventSeriesNotFound -> strings.errorEventSeriesNotFound
    is ReservationError.CapacityExceeded -> strings.errorCapacityExceeded
    is ReservationError.MultipleSeatsNotAllowed -> strings.errorMultipleSeatsNotAllowed
    is ReservationError.InvalidSeatCount -> strings.errorInvalidSeatCount
    is ReservationError.EventAlreadyFinished -> strings.errorEventAlreadyFinished
    is ReservationError.EventAlreadyStarted -> strings.errorEventAlreadyStarted
    is ReservationError.EventCancelled -> strings.errorEventCancelled
    is ReservationError.FailedToGetAllReservations -> strings.errorFailedToGetReservations
    is ReservationError.SystemError -> message
    is ReservationError.NotASeriesReservation -> strings.errorNotASeriesReservation
    is ReservationError.InstanceNotInSeries -> strings.errorInstanceNotInSeries
    is ReservationError.AlreadyOptedOut -> strings.errorAlreadyOptedOut
    is ReservationError.AlreadyCancelled -> strings.errorAlreadyCancelled
    is ReservationError.WalletNotFound -> strings.errorWalletNotFound
    is ReservationError.WalletEmpty -> strings.errorWalletEmpty
    is ReservationError.WalletEmailMismatch -> strings.errorWalletEmailMismatch
    is ReservationError.ReservationDeadlinePassed -> strings.errorReservationDeadlinePassed
    is ReservationError.EventNotFull -> strings.errorEventNotFull
    is ReservationError.WaitlistNotAvailable -> strings.errorWaitlistNotAvailable
    is ReservationError.WaitlistFull -> strings.errorWaitlistFull
    is ReservationError.AlreadyReserved -> scope.localizedMessage(strings)
    is ReservationError.AlreadyClaimed -> strings.errorReservationAlreadyClaimed
    is ReservationError.EmailDoesNotMatch -> strings.errorReservationEmailDoesNotMatch
    is ReservationError.NotClaimable -> strings.errorReservationNotClaimable
    is ReservationError.NothingToClaim -> strings.errorNothingToClaim
    is ReservationError.ClaimEmailSendFailed -> strings.errorClaimEmailSendFailed(message)
    is ReservationError.ClaimLinkInvalid -> strings.errorClaimLinkInvalid
    is ReservationError.ClaimLinkExpired -> strings.errorClaimLinkExpired
}

fun DuplicateScope.localizedMessage(strings: ErrorStrings): String = when (this) {
    DuplicateScope.SAME_EVENT -> strings.errorAlreadyReservedSameEvent
    DuplicateScope.PARENT_SERIES -> strings.errorAlreadyReservedParentSeries
    DuplicateScope.SERIES_LESSON -> strings.errorAlreadyReservedSeriesLesson
}
