package cz.svitaninymburk.projects.reservations.android.feature.reservations.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.svitaninymburk.projects.reservations.android.R
import cz.svitaninymburk.projects.reservations.android.error.toMessage
import cz.svitaninymburk.projects.reservations.android.feature.reservations.list.ReservationsViewModel
import cz.svitaninymburk.projects.reservations.android.feature.reservations.ui.StatusBadge
import cz.svitaninymburk.projects.reservations.android.feature.reservations.util.copyToClipboard
import cz.svitaninymburk.projects.reservations.android.feature.reservations.util.formatted
import cz.svitaninymburk.projects.reservations.android.feature.reservations.util.shareQrCode
import cz.svitaninymburk.projects.reservations.android.util.toCzkString
import cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import qrcode.QRCode
import qrcode.render.extensions.drawQRCode

@Composable
fun ReservationDetailScreen(
    item: MyReservationListItem,
    onNavigateBack: () -> Unit,
) {
    val vm: ReservationDetailViewModel = koinViewModel(
        key = item.id.toString(),
        parameters = { parametersOf(item) },
    )
    val listVm: ReservationsViewModel = koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.cancelSuccess) {
        if (state.cancelSuccess) {
            vm.consumeCancelSuccess()
            listVm.refresh()
        }
    }

    ReservationDetailContent(
        state = state,
        onBack = onNavigateBack,
        onCancelConfirm = vm::cancelReservation,
    )
}

@Composable
fun ReservationDetailContent(
    state: ReservationDetailUiState,
    onBack: () -> Unit,
    onCancelConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showCancelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.item.eventTitle) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoCard(state.item)

            if (state.item.status == Reservation.Status.PENDING_PAYMENT && state.item.paymentType == PaymentInfo.Type.BANK_TRANSFER) {
                PaymentSection(
                    paymentInfo = state.paymentInfo,
                    isLoading = state.isLoadingPayment,
                )
            }

            if (state.error != null) {
                Text(
                    text = state.error.toMessage(context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val canCancel = state.item.status == Reservation.Status.PENDING_PAYMENT || state.item.status == Reservation.Status.CONFIRMED
            if (canCancel) {
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    enabled = !state.isCancelling,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isCancelling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.reservation_cancel_button))
                }
            }

            when (state.item.status) {
                Reservation.Status.CANCELLED -> {
                    Text(
                        text = stringResource(R.string.reservation_cancelled_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Reservation.Status.REJECTED -> {
                    Text(
                        text = stringResource(R.string.reservation_rejected_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Unit
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.reservation_cancel_dialog_title)) },
            text = { Text(stringResource(R.string.reservation_cancel_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancelConfirm()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.reservation_cancel_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.reservation_cancel_dialog_dismiss))
                }
            },
        )
    }
}

@Composable
private fun InfoCard(item: MyReservationListItem) = Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            StatusBadge(item.status)
        }

        LabelValueRow(
            label = stringResource(R.string.reservation_date),
            value = item.startDateTime.formatted(),
        )
        LabelValueRow(
            label = stringResource(R.string.reservation_seats),
            value = item.seatCount.toString(),
        )
        LabelValueRow(
            label = stringResource(R.string.reservation_total_price),
            value = item.totalPrice.toCzkString(),
        )
        LabelValueRow(
            label = stringResource(R.string.reservation_payment_type),
            value = stringResource(
                when (item.paymentType) {
                    PaymentInfo.Type.BANK_TRANSFER -> R.string.reservation_payment_type_bank
                    PaymentInfo.Type.ON_SITE -> R.string.reservation_payment_type_on_site
                    PaymentInfo.Type.FREE -> R.string.reservation_payment_type_free
                }
            ),
        )
    }
}

@Composable
private fun LabelValueRow(label: String, value: String) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.End,
    )
}

@Composable
private fun PaymentSection(
    paymentInfo: MobilePaymentInfo?,
    isLoading: Boolean,
) = Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.reservation_payment_section_title),
            style = MaterialTheme.typography.titleSmall,
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (paymentInfo != null) {
            QrCodeSection(paymentInfo = paymentInfo)
            PaymentDetailsSection(paymentInfo = paymentInfo)
        }
    }
}

@Composable
private fun QrCodeSection(paymentInfo: MobilePaymentInfo) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val qrCode = remember(paymentInfo.spayd) { QRCode.ofSquares().build(paymentInfo.spayd) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(200.dp)
                .background(Color.White)
                .padding(12.dp),
        ) {
            drawQRCode(qrCode)
        }
        OutlinedButton(
            onClick = { coroutineScope.launch { shareQrCode(context, qrCode) } },
        ) {
            Text(stringResource(R.string.reservation_payment_share_qr))
        }
    }
}

@Composable
private fun PaymentDetailsSection(paymentInfo: MobilePaymentInfo) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyableRow(
            label = stringResource(R.string.reservation_payment_account),
            value = paymentInfo.accountNumber,
            onCopy = { copyToClipboard(context, paymentInfo.accountNumber) },
        )
        paymentInfo.variableSymbol?.let { vs ->
            CopyableRow(
                label = stringResource(R.string.reservation_payment_variable_symbol),
                value = vs,
                onCopy = { copyToClipboard(context, vs) },
            )
        }
        LabelValueRow(
            label = stringResource(R.string.reservation_payment_amount),
            value = paymentInfo.amount.toCzkString(),
        )
    }
}

@Composable
private fun CopyableRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = onCopy,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(text = stringResource(R.string.reservation_copy_action), style = MaterialTheme.typography.labelSmall)
        }
    }
}


