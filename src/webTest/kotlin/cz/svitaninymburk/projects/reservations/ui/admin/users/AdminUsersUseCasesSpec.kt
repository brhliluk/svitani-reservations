package cz.svitaninymburk.projects.reservations.ui.admin.users

import cz.svitaninymburk.projects.reservations.admin.AdminUserListItem
import cz.svitaninymburk.projects.reservations.ui.admin.users.usecase.filterUsers
import cz.svitaninymburk.projects.reservations.ui.admin.users.usecase.toggledRole
import cz.svitaninymburk.projects.reservations.user.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

private fun user(
    name: String,
    surname: String,
    email: String,
    role: User.Role = User.Role.USER,
) = AdminUserListItem(
    id = Uuid.random(),
    name = name,
    surname = surname,
    email = email,
    role = role,
    authType = AdminUserListItem.AuthType.EMAIL,
    reservationCount = 0,
)

private val USERS = listOf(
    user("Jan", "Novák", "jan.novak@example.com"),
    user("Petra", "Svobodová", "petra@svoboda.cz"),
    user("Eva", "Nová", "eva.nova@example.com"),
)

class AdminUsersUseCasesSpec {

    @Test
    fun blankQueryLeavesTheListUntouched() {
        assertEquals(USERS, filterUsers(USERS, null))
        assertEquals(USERS, filterUsers(USERS, ""))
        assertEquals(USERS, filterUsers(USERS, "   "))
    }

    @Test
    fun filterMatchesNameSurnameOrEmail() {
        assertEquals(listOf("Jan"), filterUsers(USERS, "Jan").map { it.name })
        assertEquals(listOf("Petra"), filterUsers(USERS, "Svobodová").map { it.name })
        assertEquals(listOf("Petra"), filterUsers(USERS, "svoboda.cz").map { it.name })
    }

    @Test
    fun filterIgnoresCaseOnBothSides() {
        assertEquals(listOf("Jan"), filterUsers(USERS, "NOVÁK").map { it.name })
        assertEquals(listOf("Jan"), filterUsers(USERS, "JAN.Novak@EXAMPLE.com").map { it.name })
    }

    @Test
    fun filterMatchesSubstringsSoPrefixHitsMultiplePeople() {
        // "Nov" sedí na Nováka i Novou — hledá se podřetězcem, ne od začátku slova.
        assertEquals(listOf("Jan", "Eva"), filterUsers(USERS, "Nov").map { it.name })
    }

    @Test
    fun noMatchGivesEmptyList() {
        assertEquals(emptyList(), filterUsers(USERS, "Dvořák"))
    }

    @Test
    fun roleOnlyEverFlipsBetweenAdminAndUser() {
        assertEquals(User.Role.USER, toggledRole(User.Role.ADMIN))
        assertEquals(User.Role.ADMIN, toggledRole(User.Role.USER))
    }
}
