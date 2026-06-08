package cz.svitaninymburk.projects.reservations.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun SvitaniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SvitaniDarkColorScheme else SvitaniLightColorScheme,
        typography = SvitaniTypography,
        content = content,
    )
}
