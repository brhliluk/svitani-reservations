package cz.svitaninymburk.projects.reservations.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cz.svitaninymburk.projects.reservations.android.feature.login.LoginScreen
import cz.svitaninymburk.projects.reservations.android.main.MainScreen
import cz.svitaninymburk.projects.reservations.android.repository.AuthRepository
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable data object LoginEntry : NavKey
@Serializable data object MainEntry : NavKey

@Composable
fun App() {
    val authRepository: AuthRepository = koinInject()
    val startEntry: NavKey = remember { if (authRepository.hasToken()) MainEntry else LoginEntry }
    val backStack = rememberNavBackStack(startEntry)

    MaterialTheme {
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
