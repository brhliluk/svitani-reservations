package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.calculateTotalPrice
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.WalletLookup
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.activeCustomFieldValidation
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.effectivePaymentType
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.isCompleteWalletCode
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.isReservationFormValid
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.remainingToPay
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.showsPaymentTypePicker
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.submittedSeatCount
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.walletDeduction
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.util.PhoneNumber
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Stav rezervačního formuláře. Odesílání si drží volající obrazovka
 * (`isSubmitting` + `onSubmit`), model odpovídá za to, co je ve formuláři
 * vyplněné, kolik to stojí a jestli to jde poslat.
 */
class ReservationFormModel(
    scope: CoroutineScope,
    private val wallet: WalletLookup,
    private val target: ReservationTarget,
    user: User?,
    initialWalletCode: String?,
    private val asWaitlist: Boolean,
) : ScreenModel(scope) {

    // Prefill z přihlášeného uživatele; User model nemá telefon, ten se vyplňuje vždy.
    var firstName by mutableStateOf(user?.name.orEmpty()); private set
    var lastName by mutableStateOf(user?.surname.orEmpty()); private set
    var email by mutableStateOf(user?.email.orEmpty()); private set
    var phone by mutableStateOf(""); private set

    var seats by mutableIntStateOf(1); private set
    var seatsExceeded by mutableStateOf(false); private set
    var paymentType by mutableStateOf(PaymentInfo.Type.BANK_TRANSFER); private set

    val customValues = mutableStateMapOf<String, CustomFieldValue>()

    var walletCode by mutableStateOf(initialWalletCode ?: ""); private set
    var walletInfo: WalletInfo? by mutableStateOf(null); private set

    /** Předvyplněný kód znamená, že uživatel přišel z peněženky — sekci rozbalíme. */
    var walletExpanded by mutableStateOf(initialWalletCode != null); private set

    // --- Odvozené hodnoty ---

    val total: Double
        get() = calculateTotalPrice(
            basePrice = target.price,
            seatCount = seats,
            customFields = target.customFields,
            customValues = customValues,
        )

    val deduction: Double get() = walletDeduction(walletInfo?.balance, total)
    val remaining: Double get() = remainingToPay(total, deduction)
    val showsPaymentPicker: Boolean get() = showsPaymentTypePicker(total, deduction)

    val isValid: Boolean
        get() = isReservationFormValid(
            target = target,
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone,
            seats = seats,
            customValues = customValues,
            variant = activeCustomFieldValidation,
        )

    // --- Editace ---

    fun setFirstName(value: String) { firstName = value }
    fun setLastName(value: String) { lastName = value }
    fun setPhone(value: String) { phone = value }

    /** Telefon se po odchodu z pole srovná do jednoho tvaru. */
    fun normalizePhone() {
        if (phone.isNotBlank()) phone = PhoneNumber.format(phone)
    }

    /** Změna e-mailu mění i to, komu peněženka patří, takže se ověří znovu. */
    fun setEmail(value: String) {
        email = value
        if (isCompleteWalletCode(walletCode)) lookUpWallet()
    }

    fun setSeats(typed: Int?) {
        seats = target.clampSeatCount(typed)
        seatsExceeded = target.exceedsRemainingCapacity(typed)
    }

    fun setPaymentType(value: PaymentInfo.Type) { paymentType = value }

    fun setWalletExpanded(value: Boolean) {
        walletExpanded = value
        // Sbalení sekce peněženku odpojí, aby se z ní tiše neplatilo.
        if (!value) {
            walletCode = ""
            walletInfo = null
        }
    }

    fun setWalletCode(value: String) {
        walletCode = value
        if (isCompleteWalletCode(value)) lookUpWallet() else walletInfo = null
    }

    /** Vyvolá se i při otevření formuláře s předvyplněným kódem. */
    fun lookUpWallet() {
        if (!isCompleteWalletCode(walletCode) || email.isBlank()) return
        val code = walletCode
        val forEmail = email
        scope.launch {
            walletInfo = wallet.info(code, forEmail)
        }
    }

    fun formData(locale: String): ReservationFormData = ReservationFormData(
        name = firstName,
        surname = lastName,
        email = email,
        phone = phone,
        seats = submittedSeatCount(asWaitlist, seats),
        paymentType = effectivePaymentType(total, deduction, paymentType),
        customValues = customValues,
        locale = locale,
        walletCode = walletCode.ifBlank { null },
        asWaitlist = asWaitlist,
    )
}

fun IComponent.buildReservationFormModel(
    scope: CoroutineScope,
    target: ReservationTarget,
    user: User?,
    initialWalletCode: String?,
    asWaitlist: Boolean,
): ReservationFormModel {
    val reservations = getService<ReservationServiceInterface>(RpcSerializersModules)
    return ReservationFormModel(
        scope = scope,
        wallet = WalletLookup(reservations),
        target = target,
        user = user,
        initialWalletCode = initialWalletCode,
        asWaitlist = asWaitlist,
    )
}
