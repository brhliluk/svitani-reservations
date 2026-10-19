package cz.svitaninymburk.projects.reservations.ui.admin.users.usecase

import cz.svitaninymburk.projects.reservations.admin.AdminUserListItem
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.user.User
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/**
 * `getAllUsers` vrací celý seznam, takže se hledá lokálně nad tím, co už je
 * načtené — žádný dotaz na server, žádné stránkování. Prázdný dotaz vrací
 * seznam nedotčený.
 */
fun filterUsers(users: List<AdminUserListItem>, query: String?): List<AdminUserListItem> {
    if (query.isNullOrBlank()) return users
    val needle = query.lowercase()
    return users.filter {
        it.name.lowercase().contains(needle) ||
            it.surname.lowercase().contains(needle) ||
            it.email.lowercase().contains(needle)
    }
}

/** Role se jen překlápí — jiná možnost než admin/uživatel není. */
fun toggledRole(role: User.Role): User.Role =
    if (role == User.Role.ADMIN) User.Role.USER else User.Role.ADMIN

// --- UseCase třídy (tenké, vrací Either) ---

class AdminUsersQueries(private val admin: AdminServiceInterface) {
    suspend fun users() = admin.getAllUsers()
}

class AdminUsersMutations(
    private val admin: AdminServiceInterface,
    private val auth: AuthServiceInterface,
) {
    suspend fun updateRole(id: Uuid, role: User.Role) = admin.updateUserRole(id, role)
    suspend fun delete(id: Uuid) = admin.deleteUser(id)
    suspend fun requestPasswordReset(email: String) = auth.requestPasswordReset(email)
}
