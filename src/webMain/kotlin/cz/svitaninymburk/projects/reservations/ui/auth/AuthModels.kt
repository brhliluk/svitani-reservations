package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.service.UserServiceInterface
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.AuthMutations
import cz.svitaninymburk.projects.reservations.ui.util.FormModel
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.UserPasswordMutations
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.isChangePasswordFormValid
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.isLoginFormValid
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.isRegisterFormValid
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.isResetPasswordFormValid
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.showsPasswordMismatch
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope

class LoginModel(
    scope: CoroutineScope,
    private val auth: AuthMutations,
) : FormModel(scope) {

    var email by mutableStateOf(""); private set
    var password by mutableStateOf(""); private set

    val isFormValid: Boolean get() = isLoginFormValid(email, password)

    fun setEmail(value: String) { email = value }
    fun setPassword(value: String) { password = value }

    fun login(onLoggedIn: () -> Unit) {
        if (!isFormValid) return
        submit(
            localize = { it.localizedMessage(currentStrings) },
            block = { auth.login(email, password) },
            onSuccess = { onLoggedIn() },
        )
    }
}

class RegisterModel(
    scope: CoroutineScope,
    private val auth: AuthMutations,
) : FormModel(scope) {

    var name by mutableStateOf(""); private set
    var surname by mutableStateOf(""); private set
    var email by mutableStateOf(""); private set
    var password by mutableStateOf(""); private set
    var passwordConfirm by mutableStateOf(""); private set

    val isFormValid: Boolean get() = isRegisterFormValid(name, surname, email, password, passwordConfirm)
    val showsMismatch: Boolean get() = showsPasswordMismatch(password, passwordConfirm)

    fun setName(value: String) { name = value }
    fun setSurname(value: String) { surname = value }
    fun setEmail(value: String) { email = value }
    fun setPassword(value: String) { password = value }
    fun setPasswordConfirm(value: String) { passwordConfirm = value }

    /**
     * Po registraci se hned přihlásí, aby uživatel nemusel psát heslo dvakrát.
     * Selže-li to přihlášení, registrace už proběhla — proto se nehlásí chyba,
     * ale nabídne přihlašovací dialog.
     */
    fun register(onRegistered: () -> Unit, onNeedsManualLogin: () -> Unit) {
        if (!isFormValid) return
        submit(
            localize = { it.localizedMessage(currentStrings) },
            block = { auth.register(email, password, name, surname) },
            onSuccess = {
                submit(
                    localize = { it.localizedMessage(currentStrings) },
            block = { auth.login(email, password) },
                    onSuccess = { onRegistered() },
                    onFailure = { onNeedsManualLogin() },
                )
            },
        )
    }
}

class ForgotPasswordModel(
    scope: CoroutineScope,
    private val auth: AuthMutations,
) : FormModel(scope) {

    var email by mutableStateOf(""); private set

    val isFormValid: Boolean get() = email.isNotBlank()

    fun setEmail(value: String) { email = value }

    /** Výsledek si zobrazuje volající obrazovka toastem, dialog se zavírá. */
    fun requestReset(onSent: () -> Unit, onFailed: (String) -> Unit) {
        if (!isFormValid) return
        submit(
            localize = { it.localizedMessage(currentStrings) },
            block = { auth.requestPasswordReset(email) },
            onSuccess = { onSent() },
            onFailure = onFailed,
        )
    }
}

class ChangePasswordModel(
    scope: CoroutineScope,
    private val user: UserPasswordMutations,
) : FormModel(scope) {

    var oldPassword by mutableStateOf(""); private set
    var newPassword by mutableStateOf(""); private set
    var confirmPassword by mutableStateOf(""); private set

    val isFormValid: Boolean get() = isChangePasswordFormValid(oldPassword, newPassword, confirmPassword)
    val showsMismatch: Boolean get() = showsPasswordMismatch(newPassword, confirmPassword)

    fun setOldPassword(value: String) { oldPassword = value }
    fun setNewPassword(value: String) { newPassword = value }
    fun setConfirmPassword(value: String) { confirmPassword = value }

    fun submitChange(onChanged: () -> Unit) {
        if (!isFormValid) return
        submit(
            localize = { it.localizedMessage(currentStrings) },
            block = { user.changePassword(oldPassword, newPassword) },
            onSuccess = { onChanged() },
        )
    }
}

class ResetPasswordModel(
    scope: CoroutineScope,
    private val auth: AuthMutations,
    private val token: String,
) : FormModel(scope) {

    var password by mutableStateOf(""); private set
    var passwordConfirm by mutableStateOf(""); private set

    val isFormValid: Boolean get() = isResetPasswordFormValid(password, passwordConfirm)
    val showsMismatch: Boolean get() = showsPasswordMismatch(password, passwordConfirm)

    fun setPassword(value: String) { password = value }
    fun setPasswordConfirm(value: String) { passwordConfirm = value }

    fun resetPassword(onReset: () -> Unit) {
        if (!isFormValid) return
        submit(
            localize = { it.localizedMessage(currentStrings) },
            block = { auth.resetPassword(token, password) },
            onSuccess = { onReset() },
        )
    }
}

// --- Builders ---

private fun IComponent.authMutations() = AuthMutations(getService<AuthServiceInterface>(RpcSerializersModules))

fun IComponent.buildLoginModel(scope: CoroutineScope) = LoginModel(scope, authMutations())

fun IComponent.buildRegisterModel(scope: CoroutineScope) = RegisterModel(scope, authMutations())

fun IComponent.buildForgotPasswordModel(scope: CoroutineScope) = ForgotPasswordModel(scope, authMutations())

fun IComponent.buildResetPasswordModel(scope: CoroutineScope, token: String) =
    ResetPasswordModel(scope, authMutations(), token)

fun IComponent.buildChangePasswordModel(scope: CoroutineScope) = ChangePasswordModel(
    scope,
    UserPasswordMutations(getService<UserServiceInterface>(RpcSerializersModules)),
)
