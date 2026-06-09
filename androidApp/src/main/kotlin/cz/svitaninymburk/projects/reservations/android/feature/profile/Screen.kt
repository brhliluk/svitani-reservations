package cz.svitaninymburk.projects.reservations.android.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.svitaninymburk.projects.reservations.android.R
import cz.svitaninymburk.projects.reservations.android.error.toMessage
import cz.svitaninymburk.projects.reservations.android.util.toCzkString
import cz.svitaninymburk.projects.reservations.auth.UserDto
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val vm: ProfileViewModel = koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onRetry = vm::loadWallet,
        onLogout = { vm.logout(); onLogout() },
    )
}

@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.profile_title),
            style = MaterialTheme.typography.titleLarge,
        )

        state.user?.let { UserCard(it) }

        WalletCard(state = state, onRetry = onRetry)

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.profile_logout))
        }
    }
}

@Composable
private fun WalletCard(state: ProfileUiState, onRetry: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_wallet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            when {
                state.isLoading -> WalletLoadingState()
                state.error != null -> WalletErrorState(message = state.error.toMessage(LocalContext.current), onRetry = onRetry)
                state.wallet != null -> WalletContent(state.wallet)
            }
        }
    }
}

@Composable
private fun UserCard(user: UserDto) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(user.fullName, style = MaterialTheme.typography.titleMedium)
            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WalletLoadingState() = Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center,
) { CircularProgressIndicator(modifier = Modifier.size(32.dp)) }

@Composable
private fun WalletContent(wallet: WalletInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WalletRow(
            label = stringResource(R.string.profile_wallet_code),
            value = wallet.code,
        )
        WalletRow(
            label = stringResource(R.string.profile_wallet_balance),
            value = wallet.balance.toCzkString(),
        )
        WalletRow(
            label = stringResource(R.string.profile_wallet_expires),
            value = "${wallet.seasonResetDay}. ${wallet.seasonResetMonth}.",
        )
    }
}

@Composable
private fun WalletRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WalletErrorState(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.profile_wallet_retry))
        }
    }
}
