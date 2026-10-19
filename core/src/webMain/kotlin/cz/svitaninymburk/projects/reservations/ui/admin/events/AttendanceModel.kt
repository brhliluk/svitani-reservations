package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.ATTENDANCE_DEFAULT_EXTRA_ROWS
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.AttendanceQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.clampExtraRows
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

sealed interface AttendanceUiState {
    data object Loading : AttendanceUiState
    data class Success(val data: AdminEventDetailData) : AttendanceUiState
    data class Error(val message: String) : AttendanceUiState
}

/** Prezenční listina k vytištění — seznam účastníků plus pár prázdných řádků na dopsání. */
class AttendanceModel(
    scope: CoroutineScope,
    private val queries: AttendanceQueries,
    private val eventId: String,
    private val isSeries: Boolean,
) : ScreenModel(scope) {

    var uiState by mutableStateOf<AttendanceUiState>(AttendanceUiState.Loading); private set
    var extraRows by mutableIntStateOf(ATTENDANCE_DEFAULT_EXTRA_ROWS); private set

    fun load() {
        scope.launch {
            val uuid = try {
                Uuid.parse(eventId)
            } catch (_: IllegalArgumentException) {
                uiState = AttendanceUiState.Error(currentStrings.invalidEventId)
                return@launch
            }
            queries.eventDetail(uuid, isSeries)
                .onRight { uiState = AttendanceUiState.Success(it) }
                .onLeft { uiState = AttendanceUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun setExtraRows(rows: Int) { extraRows = clampExtraRows(rows) }
}

fun IComponent.buildAttendanceModel(scope: CoroutineScope, eventId: String, isSeries: Boolean): AttendanceModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AttendanceModel(scope, AttendanceQueries(admin), eventId, isSeries)
}
