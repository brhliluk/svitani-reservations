package cz.svitaninymburk.projects.reservations.ui.admin.users

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.p
import dev.kilua.html.strong

@Composable
fun IComponent.UserActionModal(
    action: PendingUserAction,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentStrings by strings
    val isRoleChange = action.type == UserAction.CHANGE_ROLE

    ConfirmModal(
        title = if (isRoleChange) currentStrings.modalChangeRoleTitle else currentStrings.modalDeleteUserTitle,
        confirmLabel = if (isRoleChange) currentStrings.modalConfirmAction else currentStrings.modalConfirmCancelAction,
        dismissLabel = currentStrings.modalBack,
        isLoading = isLoading,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmClassName = if (isRoleChange) "btn-primary" else "btn-error",
    ) {
        p(className = "py-4") {
            if (isRoleChange) {
                +currentStrings.modalChangeRoleMsgPre
                strong { +action.userName }
                +currentStrings.modalChangeRoleMsgMid
                strong {
                    +(if (action.newRole == User.Role.ADMIN) currentStrings.roleAdmin else currentStrings.roleUser)
                }
                +currentStrings.modalChangeRoleMsgPost
            } else {
                +currentStrings.modalDeleteUserMsgPre
                strong { +action.userName }
                +currentStrings.modalDeleteUserMsgPost
            }
        }
    }
}
