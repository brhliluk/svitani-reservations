package cz.svitaninymburk.projects.reservations.android.feature.events.reservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthRepository
import cz.svitaninymburk.projects.reservations.android.repository.event.EventsRepository
import cz.svitaninymburk.projects.reservations.android.repository.reservation.ReservationsRepository
import cz.svitaninymburk.projects.reservations.android.repository.wallet.WalletRepository
import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.NumberValue
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import cz.svitaninymburk.projects.reservations.event.calculateTotalPrice
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val EMAIL_REGEX = Regex(".+@.+\\..+")

data class ReservationFormUiState(
    val isLoading: Boolean = true,
    val target: ReservationTarget? = null,
    val loadError: RepositoryError? = null,
    val contactName: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val seatCount: Int = 1,
    val paymentType: PaymentInfo.Type? = null,
    val customValues: Map<String, CustomFieldValue> = emptyMap(),
    val wallet: WalletInfo? = null,
    val useWallet: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: RepositoryError? = null,
    val createdReservation: MyReservationListItem? = null,
) {
    /** Živý náhled ceny — stejná sdílená funkce používá web i server. */
    val totalPrice: Double
        get() = target?.let { calculateTotalPrice(it.price, seatCount, it.customFields, customValues) } ?: 0.0

    val walletDeduction: Double
        get() = if (useWallet) wallet?.let { minOf(it.balance, totalPrice) } ?: 0.0 else 0.0

    val amountToPay: Double
        get() = totalPrice - walletDeduction

    val isEmailInvalid: Boolean
        get() = contactEmail.isNotBlank() && !EMAIL_REGEX.matches(contactEmail)

    val isValid: Boolean
        get() {
            val t = target ?: return false
            if (contactName.isBlank() || contactPhone.trim().length < 9) return false
            if (!EMAIL_REGEX.matches(contactEmail)) return false
            if (amountToPay > 0.0 && paymentType == null) return false
            return t.customFields.filter { it.isRequired }.all { field ->
                val value = customValues[field.key]
                when (field) {
                    is BooleanFieldDefinition -> true
                    is TextFieldDefinition -> (value as? TextValue)?.value?.isNotBlank() == true
                    is NumberFieldDefinition -> value is NumberValue
                    is TimeRangeFieldDefinition -> {
                        val range = value as? TimeRangeValue ?: return@all false
                        range.from < range.to &&
                            range.from in t.startDateTime.time..t.endDateTime.time &&
                            range.to in t.startDateTime.time..t.endDateTime.time
                    }
                }
            }
        }
}

class ReservationFormViewModel(
    private val id: Uuid,
    private val isSeries: Boolean,
    private val eventsRepository: EventsRepository,
    private val reservationsRepository: ReservationsRepository,
    private val walletRepository: WalletRepository,
    private val authRepository: AuthRepository,
    private val locale: String = "cs",
) : ViewModel() {

    val uiState: StateFlow<ReservationFormUiState>
        field = MutableStateFlow(ReservationFormUiState())

    init {
        load()
    }

    fun load() {
        uiState.update { it.copy(isLoading = true, loadError = null) }
        authRepository.getUser()?.let { user ->
            uiState.update {
                it.copy(
                    contactName = it.contactName.ifBlank { user.fullName },
                    contactEmail = it.contactEmail.ifBlank { user.email },
                )
            }
        }
        viewModelScope.launch {
            val targetResult: Either<RepositoryError, ReservationTarget> = if (isSeries) {
                eventsRepository.getSeriesDetail(id).map { ReservationTarget.Series(it.series) }
            } else {
                eventsRepository.getInstance(id).map { ReservationTarget.Instance(it) }
            }
            targetResult
                .onLeft { error -> uiState.update { it.copy(isLoading = false, loadError = error) } }
                .onRight { target ->
                    uiState.update {
                        it.copy(
                            isLoading = false,
                            target = target,
                            paymentType = target.allowedPaymentTypes.firstOrNull(),
                        )
                    }
                }
        }
        viewModelScope.launch {
            // Uživatel bez peněženky (nebo s nulovým kreditem) přepínač prostě neuvidí — není to chyba.
            walletRepository.getMyWallet().onRight { wallet ->
                if (wallet.balance > 0.0) uiState.update { it.copy(wallet = wallet) }
            }
        }
    }

    fun setContactName(value: String) = uiState.update { it.copy(contactName = value) }
    fun setContactEmail(value: String) = uiState.update { it.copy(contactEmail = value) }
    fun setContactPhone(value: String) = uiState.update { it.copy(contactPhone = value) }
    fun setPaymentType(value: PaymentInfo.Type) = uiState.update { it.copy(paymentType = value) }
    fun setUseWallet(value: Boolean) = uiState.update { it.copy(useWallet = value) }

    fun setSeatCount(value: Int) = uiState.update {
        it.copy(seatCount = value.coerceIn(1, maxOf(1, it.target?.maxCapacity ?: 1)))
    }

    fun setCustomValue(value: CustomFieldValue) = uiState.update {
        it.copy(customValues = it.customValues + (value.fieldKey to value))
    }

    fun submit() {
        val state = uiState.value
        val target = state.target ?: return
        if (!state.isValid || state.isSubmitting || state.createdReservation != null) return
        uiState.update { it.copy(isSubmitting = true, submitError = null) }

        // Zrcadlí web (Form.kt): kredit pokrývá vše (vč. ceny 0) → FREE
        val effectivePaymentType =
            if (state.amountToPay == 0.0) PaymentInfo.Type.FREE else state.paymentType!!
        val walletCode = if (state.useWallet) state.wallet?.code else null

        viewModelScope.launch {
            val result = when (target) {
                is ReservationTarget.Instance -> reservationsRepository.createInstanceReservation(
                    CreateInstanceReservationRequest(
                        eventInstanceId = target.id,
                        seatCount = state.seatCount,
                        contactName = state.contactName.trim(),
                        contactEmail = state.contactEmail.trim(),
                        contactPhone = state.contactPhone.trim(),
                        paymentType = effectivePaymentType,
                        customValues = state.customValues,
                        locale = locale,
                        walletCode = walletCode,
                    )
                )
                is ReservationTarget.Series -> reservationsRepository.createSeriesReservation(
                    CreateSeriesReservationRequest(
                        eventSeriesId = target.id,
                        seatCount = state.seatCount,
                        contactName = state.contactName.trim(),
                        contactEmail = state.contactEmail.trim(),
                        contactPhone = state.contactPhone.trim(),
                        paymentType = effectivePaymentType,
                        customValues = state.customValues,
                        locale = locale,
                        walletCode = walletCode,
                    )
                )
            }
            result
                .onLeft { error -> uiState.update { it.copy(isSubmitting = false, submitError = error) } }
                .onRight { reservation ->
                    uiState.update {
                        it.copy(isSubmitting = false, createdReservation = reservation.toListItem(target))
                    }
                }
        }
    }
}

private fun Reservation.toListItem(target: ReservationTarget) = MyReservationListItem(
    id = id,
    eventTitle = target.title,
    startDateTime = target.startDateTime,
    seatCount = seatCount,
    totalPrice = totalPrice,
    status = status,
    paymentType = paymentType,
    variableSymbol = variableSymbol,
    isSeries = reference is Reference.Series,
)
