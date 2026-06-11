package cz.svitaninymburk.projects.reservations.android.feature.events.reservation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.svitaninymburk.projects.reservations.android.R
import cz.svitaninymburk.projects.reservations.android.error.toMessage
import cz.svitaninymburk.projects.reservations.android.feature.events.ui.ErrorState
import cz.svitaninymburk.projects.reservations.android.feature.events.ui.LoadingState
import cz.svitaninymburk.projects.reservations.android.util.toCzkString
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import kotlin.uuid.Uuid
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReservationFormScreen(
    id: String,
    isSeries: Boolean,
    onNavigateBack: () -> Unit,
    onSuccess: (MyReservationListItem) -> Unit,
) {
    val vm: ReservationFormViewModel = koinViewModel(
        key = "$id-$isSeries",
        parameters = { parametersOf(Uuid.parse(id), isSeries) },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdReservation) {
        state.createdReservation?.let(onSuccess)
    }

    ReservationFormContent(
        state = state,
        onBack = onNavigateBack,
        onRetry = vm::load,
        onNameChange = vm::setContactName,
        onEmailChange = vm::setContactEmail,
        onPhoneChange = vm::setContactPhone,
        onSeatCountChange = vm::setSeatCount,
        onPaymentTypeChange = vm::setPaymentType,
        onUseWalletChange = vm::setUseWallet,
        onCustomValueChange = vm::setCustomValue,
        onSubmit = vm::submit,
    )
}

@Composable
fun ReservationFormContent(
    state: ReservationFormUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSeatCountChange: (Int) -> Unit,
    onPaymentTypeChange: (PaymentInfo.Type) -> Unit,
    onUseWalletChange: (Boolean) -> Unit,
    onCustomValueChange: (CustomFieldValue) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.target?.title ?: stringResource(R.string.reservation_form_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))
            state.loadError != null -> ErrorState(
                message = state.loadError.toMessage(context),
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding),
            )
            state.target != null -> FormBody(
                state = state,
                onNameChange = onNameChange,
                onEmailChange = onEmailChange,
                onPhoneChange = onPhoneChange,
                onSeatCountChange = onSeatCountChange,
                onPaymentTypeChange = onPaymentTypeChange,
                onUseWalletChange = onUseWalletChange,
                onCustomValueChange = onCustomValueChange,
                onSubmit = onSubmit,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun FormBody(
    state: ReservationFormUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSeatCountChange: (Int) -> Unit,
    onPaymentTypeChange: (PaymentInfo.Type) -> Unit,
    onUseWalletChange: (Boolean) -> Unit,
    onCustomValueChange: (CustomFieldValue) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val target = state.target ?: return
    val emailInvalid = state.isEmailInvalid

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PriceSummaryCard(state)

        OutlinedTextField(
            value = state.contactName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.reservation_form_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.contactEmail,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.reservation_form_email)) },
            singleLine = true,
            isError = emailInvalid,
            supportingText = if (emailInvalid) {
                { Text(stringResource(R.string.reservation_form_error_email)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.contactPhone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.reservation_form_phone)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        SeatCountRow(state, onSeatCountChange)

        target.customFields.forEach { field ->
            CustomFieldInput(
                definition = field,
                value = state.customValues[field.key],
                onChange = onCustomValueChange,
            )
        }

        if (state.amountToPay > 0.0 && target.allowedPaymentTypes.size > 1) {
            PaymentTypeSelector(state, target.allowedPaymentTypes, onPaymentTypeChange)
        }

        state.wallet?.let { wallet ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.useWallet,
                        role = Role.Switch,
                        onValueChange = onUseWalletChange,
                    ),
            ) {
                Text(
                    stringResource(R.string.reservation_form_use_wallet, wallet.balance.toCzkString()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = state.useWallet, onCheckedChange = null)
            }
        }

        if (state.submitError != null) {
            Text(
                text = state.submitError.toMessage(context),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            stringResource(R.string.reservation_form_required_legend),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onSubmit,
            enabled = state.isValid && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.reservation_form_submit))
        }
    }
}

@Composable
private fun PriceSummaryCard(state: ReservationFormUiState) = Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryRow(
            label = stringResource(R.string.reservation_form_total_price),
            value = if (state.totalPrice == 0.0) stringResource(R.string.reservation_form_free)
                else state.totalPrice.toCzkString(),
            emphasized = state.walletDeduction == 0.0,
        )
        if (state.walletDeduction > 0.0) {
            SummaryRow(
                label = stringResource(R.string.reservation_form_wallet_deduction),
                value = "− ${state.walletDeduction.toCzkString()}",
            )
            SummaryRow(
                label = stringResource(R.string.reservation_form_remaining_to_pay),
                value = if (state.amountToPay == 0.0) stringResource(R.string.reservation_form_free)
                    else state.amountToPay.toCzkString(),
                emphasized = true,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, emphasized: Boolean = false) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        value,
        style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SeatCountRow(state: ReservationFormUiState, onSeatCountChange: (Int) -> Unit) {
    val max = state.target?.maxCapacity ?: 1
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(stringResource(R.string.reservation_form_seats), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.reservation_form_seats_max, max),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSeatCountChange(state.seatCount - 1) },
                enabled = state.seatCount > 1,
            ) { Text("−") }
            Text(state.seatCount.toString(), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { onSeatCountChange(state.seatCount + 1) },
                enabled = state.seatCount < max,
            ) { Text("+") }
        }
    }
}

@Composable
private fun PaymentTypeSelector(
    state: ReservationFormUiState,
    allowed: List<PaymentInfo.Type>,
    onPaymentTypeChange: (PaymentInfo.Type) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.reservation_form_payment_type), style = MaterialTheme.typography.bodyMedium)
        allowed.filter { it != PaymentInfo.Type.FREE }.forEach { type ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.paymentType == type,
                        role = Role.RadioButton,
                        onClick = { onPaymentTypeChange(type) },
                    ),
            ) {
                RadioButton(
                    selected = state.paymentType == type,
                    onClick = null,
                )
                Text(
                    stringResource(
                        when (type) {
                            PaymentInfo.Type.BANK_TRANSFER -> R.string.reservation_payment_type_bank
                            PaymentInfo.Type.ON_SITE -> R.string.reservation_payment_type_on_site
                            PaymentInfo.Type.FREE -> R.string.reservation_payment_type_free
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
