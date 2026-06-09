package cz.svitaninymburk.projects.reservations.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SvitaniStatusColors(
    val pendingContent: Color,
    val pendingContainer: Color,
    val confirmedContent: Color,
    val confirmedContainer: Color,
    val neutralContent: Color,
    val neutralContainer: Color,
)

private val LightStatusColors = SvitaniStatusColors(
    pendingContent = StatusPendingContentLight,
    pendingContainer = StatusPendingContainerLight,
    confirmedContent = StatusConfirmedContentLight,
    confirmedContainer = StatusConfirmedContainerLight,
    neutralContent = StatusNeutralContentLight,
    neutralContainer = StatusNeutralContainerLight,
)

private val DarkStatusColors = SvitaniStatusColors(
    pendingContent = StatusPendingContentDark,
    pendingContainer = StatusPendingContainerDark,
    confirmedContent = StatusConfirmedContentDark,
    confirmedContainer = StatusConfirmedContainerDark,
    neutralContent = StatusNeutralContentDark,
    neutralContainer = StatusNeutralContainerDark,
)

val LocalSvitaniStatusColors = staticCompositionLocalOf { LightStatusColors }

val MaterialTheme.statusColors: SvitaniStatusColors
    @Composable get() = LocalSvitaniStatusColors.current

@Composable
fun SvitaniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSvitaniStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) SvitaniDarkColorScheme else SvitaniLightColorScheme,
            typography = SvitaniTypography,
            content = content,
        )
    }
}
