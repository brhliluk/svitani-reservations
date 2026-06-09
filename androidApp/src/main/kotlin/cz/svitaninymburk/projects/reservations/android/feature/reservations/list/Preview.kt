package cz.svitaninymburk.projects.reservations.android.feature.reservations.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.ui.theme.SvitaniTheme
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

private class ReservationsUiStateProvider : PreviewParameterProvider<ReservationsUiState> {
    override val values = sequenceOf(
        ReservationsUiState(isLoading = true),
        ReservationsUiState(items = emptyList()),
        ReservationsUiState(error = RepositoryError.Network),
        ReservationsUiState(items = listOf(previewItemConfirmed, previewItemPending, previewItemCancelled)),
    )
}

@PreviewLightDark
@Composable
private fun ReservationListContentPreview(
    @PreviewParameter(ReservationsUiStateProvider::class) state: ReservationsUiState,
) {
    SvitaniTheme {
        ReservationListContent(
            state = state,
            onReservationClick = {},
            onRetry = {},
            onRefresh = {},
        )
    }
}

private val previewItemConfirmed = MyReservationListItem(
    id = Uuid.random(),
    eventTitle = "Jóga pro začátečníky",
    startDateTime = LocalDateTime(2026, 7, 15, 9, 0),
    seatCount = 2,
    totalPrice = 500.0,
    status = Reservation.Status.CONFIRMED,
    paymentType = PaymentInfo.Type.BANK_TRANSFER,
    variableSymbol = "1234567890",
    isSeries = false,
)

private val previewItemPending = MyReservationListItem(
    id = Uuid.random(),
    eventTitle = "Pilates — série lekcí",
    startDateTime = LocalDateTime(2026, 8, 1, 10, 0),
    seatCount = 1,
    totalPrice = 1800.0,
    status = Reservation.Status.PENDING_PAYMENT,
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
