package cz.svitaninymburk.projects.reservations.android.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.svitaninymburk.projects.reservations.android.R
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthRepository
import org.koin.compose.koinInject

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val authRepository: AuthRepository = koinInject()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = {
                authRepository.clearTokens()
                onLogout()
            }) {
                Text(stringResource(R.string.profile_logout))
            }
        }
    }
}
