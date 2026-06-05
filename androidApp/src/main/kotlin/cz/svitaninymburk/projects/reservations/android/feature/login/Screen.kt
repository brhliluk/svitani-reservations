package cz.svitaninymburk.projects.reservations.android.feature.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val viewModel: LoginViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            viewModel.consumeLoginSuccess()
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Svítání", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Přihlaste se ke svému účtu", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Heslo") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        uiState.error?.let { errorMsg ->
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = viewModel::login,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Přihlásit se")
            }
        }
    }
}
