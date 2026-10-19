package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.usecase.EventDefinitionTitleQuery
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Rozcestník "co nad šablonou založit". Název šablony je jen v podtitulku,
 * proto se chyba načtení nikam nehlásí — obrazovka funguje i bez něj.
 */
class EventCreateChooseModel(
    scope: CoroutineScope,
    private val query: EventDefinitionTitleQuery,
    private val definitionId: String,
) : ScreenModel(scope) {

    var definitionTitle by mutableStateOf<String?>(null); private set

    fun load() {
        scope.launch { definitionTitle = query.titleOf(definitionId) }
    }
}

fun IComponent.buildEventCreateChooseModel(scope: CoroutineScope, definitionId: String): EventCreateChooseModel {
    val event = getService<EventServiceInterface>(RpcSerializersModules)
    return EventCreateChooseModel(scope, EventDefinitionTitleQuery(event), definitionId)
}
