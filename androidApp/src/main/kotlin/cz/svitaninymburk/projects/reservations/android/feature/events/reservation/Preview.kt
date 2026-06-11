package cz.svitaninymburk.projects.reservations.android.feature.events.reservation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import cz.svitaninymburk.projects.reservations.android.ui.theme.SvitaniTheme
import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.PriceModifier
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDateTime

private val previewInstance = EventInstance(
    id = Uuid.random(),
    definitionId = Uuid.random(),
    title = "Jóga pro začátečníky",
    description = "Lekce jógy pro úplné začátečníky. Podložky zajištěny.",
    startDateTime = LocalDateTime(2026, 7, 15, 9, 0),
    endDateTime = LocalDateTime(2026, 7, 15, 10, 0),
    price = 200.0,
    capacity = 10,
    occupiedSpots = 3,
)

private val previewInstanceWithExtras = previewInstance.copy(
    customFields = listOf(
        BooleanFieldDefinition(
            key = "pomucky",
            label = "Zapůjčení pomůcek",
            priceModifier = PriceModifier.FixedAmount(50.0),
        ),
    ),
)

private val previewWallet = WalletInfo(
    code = "WAL12345678901",
    balance = 150.0,
    emailMatches = true,
    seasonResetDay = 1,
    seasonResetMonth = 9,
)

private fun previewState(
    target: ReservationTarget = ReservationTarget.Instance(previewInstance),
    wallet: WalletInfo? = null,
    useWallet: Boolean = false,
) = ReservationFormUiState(
    isLoading = false,
    target = target,
    contactName = "Jan Novák",
    contactEmail = "jan.novak@example.com",
    contactPhone = "+420123456789",
    seatCount = 2,
    paymentType = PaymentInfo.Type.BANK_TRANSFER,
    wallet = wallet,
    useWallet = useWallet,
)

@PreviewLightDark
@Composable
private fun ReservationFormContentPreview() {
    SvitaniTheme {
        ReservationFormContent(
            state = previewState(),
            onBack = {},
            onRetry = {},
            onNameChange = {},
            onEmailChange = {},
            onPhoneChange = {},
            onSeatCountChange = {},
            onPaymentTypeChange = {},
            onUseWalletChange = {},
            onCustomValueChange = {},
            onSubmit = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ReservationFormContentWithWalletAndCustomFieldPreview() {
    SvitaniTheme {
        ReservationFormContent(
            state = previewState(
                target = ReservationTarget.Instance(previewInstanceWithExtras),
                wallet = previewWallet,
                useWallet = true,
            ),
            onBack = {},
            onRetry = {},
            onNameChange = {},
            onEmailChange = {},
            onPhoneChange = {},
            onSeatCountChange = {},
            onPaymentTypeChange = {},
            onUseWalletChange = {},
            onCustomValueChange = {},
            onSubmit = {},
        )
    }
}
