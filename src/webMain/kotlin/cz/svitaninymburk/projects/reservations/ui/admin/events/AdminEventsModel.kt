package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.admin.EventsPage
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.AdminEventsMutations
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.AdminEventsQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.DEFINITIONS_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

sealed interface AdminEventsUiState {
    data object Loading : AdminEventsUiState
    data class Success(val data: EventsPage) : AdminEventsUiState
    data class Error(val message: String) : AdminEventsUiState
}

class AdminEventsModel(
    scope: CoroutineScope,
    private val queries: AdminEventsQueries,
    private val mutations: AdminEventsMutations,
) : ScreenModel(scope) {

    var uiState: AdminEventsUiState by mutableStateOf(AdminEventsUiState.Loading); private set
    var definitionsPage by mutableIntStateOf(0); private set
    val childrenPageByDef = mutableStateMapOf<Uuid, Int>()
    var includePast by mutableStateOf(false); private set

    var deleteDefinitionPending: AdminEventListItem? by mutableStateOf(null); private set
    var deleteItemPending: AdminEventListItem? by mutableStateOf(null); private set
    var hideItemPending: AdminEventListItem? by mutableStateOf(null); private set
    var refundMoney by mutableStateOf(true)

    // Reproduces the original produceState: previous data stays visible on refetch;
    // only the initial state is Loading.
    fun load() {
        scope.launch {
            queries.events(definitionsPage, DEFINITIONS_PAGE_SIZE, includePast)
                .onRight { uiState = AdminEventsUiState.Success(it) }
                .onLeft { uiState = AdminEventsUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun setIncludePast(value: Boolean) {
        includePast = value
        definitionsPage = 0
        load()
    }

    fun setDefinitionsPage(page: Int) {
        definitionsPage = page
        load()
    }

    fun setChildPage(defId: Uuid, page: Int) {
        childrenPageByDef[defId] = page
    }

    fun togglePublished(item: AdminEventListItem) {
        if (item.isPublished && item.occupiedSpots > 0) {
            hideItemPending = item
        } else {
            setPublished(item, !item.isPublished)
        }
    }

    fun confirmHide() {
        val item = hideItemPending ?: return
        hideItemPending = null
        setPublished(item, false)
    }

    fun dismissHide() { hideItemPending = null }

    private fun setPublished(item: AdminEventListItem, published: Boolean) = run(
        errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
        block = {
            if (item.isSeries) mutations.setSeriesPublished(item.id, published)
            else mutations.setInstancePublished(item.id, published)
        },
        onSuccess = {
            showToast(if (published) currentStrings.toastPublished else currentStrings.toastHidden)
            definitionsPage = 0
            load()
        },
    )

    fun requestDeleteDefinition(def: AdminEventListItem) { deleteDefinitionPending = def }
    fun dismissDeleteDefinition() { deleteDefinitionPending = null }
    fun confirmDeleteDefinition() {
        val def = deleteDefinitionPending ?: return
        deleteDefinitionPending = null
        run(
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.deleteDefinition(def.id) },
            onSuccess = {
                showToast(currentStrings.toastDefinitionDeleted)
                definitionsPage = 0
                load()
            },
        )
    }

    fun requestDeleteItem(item: AdminEventListItem) {
        refundMoney = true
        deleteItemPending = item
    }
    fun dismissDeleteItem() { deleteItemPending = null }
    fun confirmDeleteItem() {
        val item = deleteItemPending ?: return
        deleteItemPending = null
        run(
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = {
                if (item.isSeries) mutations.deleteSeries(item.id, refundMoney)
                else mutations.deleteInstance(item.id, refundMoney)
            },
            onSuccess = {
                showToast(if (item.isSeries) currentStrings.toastSeriesDeleted else currentStrings.toastEventDeleted)
                definitionsPage = 0
                load()
            },
        )
    }
}

fun IComponent.buildAdminEventsModel(scope: CoroutineScope): AdminEventsModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminEventsModel(
        scope = scope,
        queries = AdminEventsQueries(admin),
        mutations = AdminEventsMutations(admin),
    )
}
