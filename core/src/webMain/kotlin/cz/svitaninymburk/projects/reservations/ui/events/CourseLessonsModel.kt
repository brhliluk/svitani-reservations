package cz.svitaninymburk.projects.reservations.ui.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.ui.events.usecase.CourseLessonsLoader
import cz.svitaninymburk.projects.reservations.ui.events.usecase.CourseLessonsQueries
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Rozbalovací termíny kurzu na kartě. Rozcestník lekce kurzů nenačítá (kromě
 * jednotlivě rezervovatelných), takže se stáhnou až při prvním rozbalení.
 * Chyba se ukáže přímo v kartě, ne toastem — proto to není ScreenModel.
 */
class CourseLessonsModel(
    private val scope: CoroutineScope,
    private val loader: CourseLessonsLoader,
) {
    var isExpanded by mutableStateOf(false); private set
    var lessons by mutableStateOf<List<EventInstance>?>(null); private set
    var isLoading by mutableStateOf(false); private set
    var errorMessage by mutableStateOf<String?>(null); private set

    fun toggle() {
        isExpanded = !isExpanded
        if (isExpanded && lessons == null && !isLoading) load()
    }

    fun load() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                loader.lessons()
                    .onRight { lessons = it.sortedBy { lesson -> lesson.startDateTime } }
                    .onLeft { errorMessage = it.localizedMessage(strings.value) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                errorMessage = strings.value.loadingError(e.message ?: "unknown")
            } finally {
                isLoading = false
            }
        }
    }
}

fun IComponent.buildCourseLessonsModel(scope: CoroutineScope, seriesId: Uuid): CourseLessonsModel {
    val event = getService<EventServiceInterface>(RpcSerializersModules)
    return CourseLessonsModel(scope, CourseLessonsQueries(event, seriesId))
}
