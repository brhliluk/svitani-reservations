package cz.svitaninymburk.projects.reservations.ui.admin.schedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.SchedulePage
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.schedule.usecase.AdminScheduleQueries
import cz.svitaninymburk.projects.reservations.ui.admin.schedule.usecase.SCHEDULE_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.admin.schedule.usecase.firstUpcomingPage
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface AdminScheduleUiState {
    data object Loading : AdminScheduleUiState
    data class Success(val data: SchedulePage) : AdminScheduleUiState
    data class Error(val message: String) : AdminScheduleUiState
}

class AdminScheduleModel(
    scope: CoroutineScope,
    private val queries: AdminScheduleQueries,
) : ScreenModel(scope) {

    var uiState: AdminScheduleUiState by mutableStateOf(AdminScheduleUiState.Loading); private set
    var page by mutableIntStateOf(0); private set
    var includePast by mutableStateOf(false); private set

    // Předchozí data zůstanou viditelná i při refetchi; Loading je jen počáteční stav.
    fun load() {
        scope.launch {
            queries.schedule(page, SCHEDULE_PAGE_SIZE, includePast)
                .onRight { uiState = AdminScheduleUiState.Success(it) }
                .onLeft { uiState = AdminScheduleUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun setPage(value: Int) {
        page = value
        load()
    }

    fun setIncludePast(value: Boolean) {
        includePast = value
        // Zapnutí "i minulé" má držet pozici u "teď" — skoč na stránku, kde začínají
        // nadcházející termíny, a odtud jde stránkovat dozadu do historie.
        page = if (value) {
            val pastCount = (uiState as? AdminScheduleUiState.Success)?.data?.pastCount ?: 0L
            firstUpcomingPage(pastCount, SCHEDULE_PAGE_SIZE)
        } else {
            0
        }
        load()
    }
}

fun IComponent.buildAdminScheduleModel(scope: CoroutineScope): AdminScheduleModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminScheduleModel(scope = scope, queries = AdminScheduleQueries(admin))
}
