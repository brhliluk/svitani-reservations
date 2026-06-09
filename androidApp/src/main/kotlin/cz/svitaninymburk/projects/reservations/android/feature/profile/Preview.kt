package cz.svitaninymburk.projects.reservations.android.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.ui.theme.SvitaniTheme
import cz.svitaninymburk.projects.reservations.auth.UserDto
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import kotlin.uuid.Uuid

private class ProfileUiStateProvider : PreviewParameterProvider<ProfileUiState> {
    override val values = sequenceOf(
        ProfileUiState(isLoading = true, user = previewUser),
        ProfileUiState(isLoading = false, error = RepositoryError.Network, user = previewUser),
        ProfileUiState(isLoading = false, wallet = previewWallet, user = previewUser),
    )
}

@PreviewLightDark
@Composable
private fun ProfileContentPreview(
    @PreviewParameter(ProfileUiStateProvider::class) state: ProfileUiState,
) {
    SvitaniTheme {
        ProfileContent(
            state = state,
            onRetry = {},
            onLogout = {},
        )
    }
}

private val previewUser = UserDto(
    id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
    email = "jana.novakova@example.com",
    fullName = "Jana Nováková",
    role = User.Role.USER,
)

private val previewWallet = WalletInfo(
    code = "SVIT-ABCD-1234",
    balance = 350.0,
    emailMatches = true,
    seasonResetDay = 31,
    seasonResetMonth = 8,
)
