package cz.svitaninymburk.projects.reservations.android.feature.reservations.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.ui.theme.SvitaniTheme
import cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

private class ReservationDetailUiStateProvider : PreviewParameterProvider<ReservationDetailUiState> {
    override val values = sequenceOf(
        ReservationDetailUiState(
            item = previewItemPendingBank,
            paymentInfo = MobilePaymentInfo(
                spayd = "SPD*1.0*ACC:CZ6508000000192000145399+FEDBCZPP*AM:500.00*CC:CZK*MSG:Rezervace*X-VS:1234567890",
                amount = 500.0,
                variableSymbol = "1234567890",
                iban = "CZ6508000000192000145399",
                accountNumber = "192000145399/0800",
            ),
        ),
        ReservationDetailUiState(
            item = previewItemConfirmed,
        ),
        ReservationDetailUiState(
            item = previewItemCancelled,
        ),
        ReservationDetailUiState(
            item = previewItemPendingBank,
            isLoadingPayment = true,
        ),
        ReservationDetailUiState(
            item = previewItemPendingBank,
            error = RepositoryError.Network,
        ),
    )
}

@PreviewLightDark
@Composable
private fun ReservationDetailContentPreview(
    @PreviewParameter(ReservationDetailUiStateProvider::class) state: ReservationDetailUiState,
) {
    SvitaniTheme {
        ReservationDetailContent(
            state = state,
            onBack = {},
            onCancelConfirm = {},
        )
    }
}

private val previewItemPendingBank = MyReservationListItem(
    id = Uuid.random(),
    eventTitle = "Jóga pro začátečníky",
    startDateTime = LocalDateTime(2026, 7, 15, 9, 0),
    seatCount = 2,
    totalPrice = 500.0,
    status = Reservation.Status.PENDING_PAYMENT,
    paymentType = PaymentInfo.Type.BANK_TRANSFER,
    variableSymbol = "1234567890",
    isSeries = false,
)

private val previewItemConfirmed = MyReservationListItem(
    id = Uuid.random(),
    eventTitle = "Pilates — série lekcí",
    startDateTime = LocalDateTime(2026, 8, 1, 10, 0),
    seatCount = 1,
    totalPrice = 1800.0,
    status = Reservation.Status.CONFIRMED,
    paymentType = PaymentInfo.Type.BANK_TRANSFER,
    variableSymbol = "9876543210",
    isSeries = true,
)

private val previewItemCancelled = MyReservationListItem(
    id = Uuid.random(),
    eventTitle = "Dětská jóga",
    startDateTime = LocalDateTime(2026, 6, 1, 9, 0),
    seatCount = 1,
    totalPrice = 200.0,
    status = Reservation.Status.CANCELLED,
    paymentType = PaymentInfo.Type.ON_SITE,
    variableSymbol = null,
    isSeries = false,
)
