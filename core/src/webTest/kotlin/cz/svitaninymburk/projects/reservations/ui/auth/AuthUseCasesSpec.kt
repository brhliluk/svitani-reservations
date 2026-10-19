package cz.svitaninymburk.projects.reservations.ui.auth

import cz.svitaninymburk.projects.reservations.auth.MIN_PASSWORD_LENGTH
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.isChangePasswordFormValid
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.isLoginFormValid
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.isPasswordLongEnough
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.isRegisterFormValid
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.isResetPasswordFormValid
import cz.svitaninymburk.projects.reservations.ui.auth.usecase.showsPasswordMismatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordRulesSpec {

    @Test
    fun minimumLengthIsSharedWithTheServer() {
        // UserService.changePassword vynucuje tutéž konstantu; kdyby si každá
        // strana držela vlastní číslo, klient by pustil heslo, které server
        // odmítne.
        assertEquals(6, MIN_PASSWORD_LENGTH)
    }

    @Test
    fun passwordMustReachTheMinimum() {
        assertFalse(isPasswordLongEnough(""))
        assertFalse(isPasswordLongEnough("a".repeat(MIN_PASSWORD_LENGTH - 1)))
        assertTrue(isPasswordLongEnough("a".repeat(MIN_PASSWORD_LENGTH)))
    }

    @Test
    fun mismatchStaysQuietWhileTheConfirmationIsEmpty() {
        // Červený rámeček od prvního znaku by jen otravoval.
        assertFalse(showsPasswordMismatch("tajne1", ""))
    }

    @Test
    fun mismatchShowsAsSoonAsTheConfirmationDiffers() {
        assertTrue(showsPasswordMismatch("tajne1", "t"))
        assertTrue(showsPasswordMismatch("tajne1", "tajne2"))
        assertFalse(showsPasswordMismatch("tajne1", "tajne1"))
    }

    @Test
    fun clearingThePasswordWithAFilledConfirmationAlsoCountsAsMismatch() {
        // Registrace to dřív hlásila, změna hesla ne — teď obojí.
        assertTrue(showsPasswordMismatch("", "tajne1"))
    }
}

class AuthFormValidationSpec {

    @Test
    fun loginNeedsEmailAndPassword() {
        assertTrue(isLoginFormValid("a@b.cz", "x"))
        assertFalse(isLoginFormValid("", "x"))
        assertFalse(isLoginFormValid("a@b.cz", ""))
        assertFalse(isLoginFormValid("   ", "x"))
    }

    @Test
    fun loginDoesNotRequireAStrongPassword() {
        // Na přihlášení se délka nehlídá — heslo už existuje a mohlo vzniknout
        // pod jiným pravidlem. Hlídat ji tady by uzamklo staré účty.
        assertTrue(isLoginFormValid("a@b.cz", "x"))
    }

    @Test
    fun registrationNeedsEveryFieldAndAMatchingPassword() {
        assertTrue(isRegisterFormValid("Jan", "Novák", "a@b.cz", "tajne1", "tajne1"))
        assertFalse(isRegisterFormValid("", "Novák", "a@b.cz", "tajne1", "tajne1"))
        assertFalse(isRegisterFormValid("Jan", " ", "a@b.cz", "tajne1", "tajne1"))
        assertFalse(isRegisterFormValid("Jan", "Novák", "", "tajne1", "tajne1"))
        assertFalse(isRegisterFormValid("Jan", "Novák", "a@b.cz", "krat", "krat"))
        assertFalse(isRegisterFormValid("Jan", "Novák", "a@b.cz", "tajne1", "tajne2"))
    }

    @Test
    fun changePasswordNeedsTheOldOneToo() {
        assertTrue(isChangePasswordFormValid("stare", "tajne1", "tajne1"))
        assertFalse(isChangePasswordFormValid("", "tajne1", "tajne1"))
        assertFalse(isChangePasswordFormValid("stare", "krat", "krat"))
        assertFalse(isChangePasswordFormValid("stare", "tajne1", "tajne2"))
    }

    @Test
    fun resetPasswordOnlyNeedsTheNewPasswordTwice() {
        // Token přišel e-mailem, staré heslo se u resetu nezadává.
        assertTrue(isResetPasswordFormValid("tajne1", "tajne1"))
        assertFalse(isResetPasswordFormValid("krat", "krat"))
        assertFalse(isResetPasswordFormValid("tajne1", "tajne2"))
    }
}
