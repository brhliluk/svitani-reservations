package cz.svitaninymburk.projects.reservations.ui.auth.usecase

import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.auth.MIN_PASSWORD_LENGTH
import cz.svitaninymburk.projects.reservations.auth.RegisterRequest
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.service.UserServiceInterface

// --- Pure helpery (testovatelné bez RPC) ---

fun isPasswordLongEnough(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

/**
 * Kdy u potvrzení hesla svítit chybu. Prázdné potvrzení ještě chyba není —
 * uživatel ho teprve píše a červený rámeček od prvního znaku by jen otravoval.
 *
 * Registrace i změna hesla to měly každá po svém: registrace hlídala jen
 * prázdné potvrzení, změna hesla i prázdné nové heslo. Rozdíl se projeví, když
 * uživatel vyplní potvrzení a pak vymaže nové heslo — teď to obojí hlásí.
 */
fun showsPasswordMismatch(password: String, confirmation: String): Boolean =
    confirmation.isNotEmpty() && password != confirmation

fun isLoginFormValid(email: String, password: String): Boolean =
    email.isNotBlank() && password.isNotBlank()

fun isRegisterFormValid(
    name: String,
    surname: String,
    email: String,
    password: String,
    confirmation: String,
): Boolean =
    name.isNotBlank() &&
        surname.isNotBlank() &&
        email.isNotBlank() &&
        isPasswordLongEnough(password) &&
        password == confirmation

fun isChangePasswordFormValid(
    oldPassword: String,
    newPassword: String,
    confirmation: String,
): Boolean =
    oldPassword.isNotBlank() && isPasswordLongEnough(newPassword) && newPassword == confirmation

fun isResetPasswordFormValid(password: String, confirmation: String): Boolean =
    isPasswordLongEnough(password) && password == confirmation

// --- UseCase třídy (tenké, vrací Either) ---

class AuthMutations(private val auth: AuthServiceInterface) {
    suspend fun login(email: String, password: String) = auth.login(LoginRequest(email, password))
    suspend fun register(email: String, password: String, name: String, surname: String) =
        auth.register(RegisterRequest(email, password, name, surname))
    suspend fun requestPasswordReset(email: String) = auth.requestPasswordReset(email)
    suspend fun resetPassword(token: String, password: String) = auth.resetPassword(token, password)
}

class UserPasswordMutations(private val user: UserServiceInterface) {
    suspend fun changePassword(oldPassword: String, newPassword: String) =
        user.changePassword(oldPassword, newPassword)
}
