package cz.svitaninymburk.projects.reservations.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cz.svitaninymburk.projects.reservations.android.feature.login.LoginScreen
import cz.svitaninymburk.projects.reservations.android.main.MainScreen
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthRepository
import cz.svitaninymburk.projects.reservations.android.ui.theme.SvitaniTheme
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable data object LoginEntry : NavKey
@Serializable data object MainEntry : NavKey

@Composable
fun App() {
    val authRepository: AuthRepository = koinInject()
    var startEntry by remember { mutableStateOf<NavKey?>(null) }

    LaunchedEffect(authRepository) { startEntry = if (authRepository.hasToken()) MainEntry else LoginEntry }

    val currentStartEntry = startEntry
    if (currentStartEntry == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val backStack = rememberNavBackStack(currentStartEntry)

    SvitaniTheme {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<LoginEntry> {
                    LoginScreen(
                        onLoginSuccess = {
                            backStack.clear()
                            backStack.add(MainEntry)
                        }
                    )
                }
                entry<MainEntry> {
                    MainScreen(
                        onLogout = {
                            backStack.clear()
                            backStack.add(LoginEntry)
                        }
                    )
                }
            },
        )
    }
}
