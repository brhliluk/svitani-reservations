package cz.svitaninymburk.projects.reservations.slot.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AuditLogPage
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.audit.AuditEvent
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.slot.AvailabilityException
import cz.svitaninymburk.projects.reservations.slot.AvailabilityRule
import cz.svitaninymburk.projects.reservations.slot.ScheduleProblem
import cz.svitaninymburk.projects.reservations.slot.SlotAdminError
import cz.svitaninymburk.projects.reservations.slot.SlotDayOverview
import cz.svitaninymburk.projects.reservations.slot.SlotReservationRow
import cz.svitaninymburk.projects.reservations.slot.SlotService
import cz.svitaninymburk.projects.reservations.slot.SlotServiceDetail
import cz.svitaninymburk.projects.reservations.slot.SlotServiceProblem
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.ScheduleDraft
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.SlotAdminMutations
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.SlotAdminQueries
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.SlotAuditResend
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.SlotServiceForm
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.formOf
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.formProblems
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.newServiceForm
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.scheduleDraftOf
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.shiftDay
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.toSchedule
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.toService
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotAdminStrings
import cz.svitaninymburk.projects.reservations.slot.ui.slotAdminService
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dev.kilua.rpc.getService
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** „Dnes“ podle prohlížeče — admin je v Praze, na denní přehled to stačí. */
internal fun browserToday(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

private suspend fun <T> loadCatching(block: suspend () -> T, failure: (Exception) -> String): LoadState<T> = try {
    LoadState.Loaded(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    LoadState.Failed(failure(e))
}

// --- Seznam služeb ---

class AdminSlotServicesModel(scope: CoroutineScope, private val queries: SlotAdminQueries) : ScreenModel(scope) {
    var state: LoadState<List<SlotService>> by mutableStateOf(LoadState.Loading); private set

    fun load() {
        state = LoadState.Loading
        scope.launch { state = loadCatching({ queries.services() }) { slotAdminStrings.loadError } }
    }
}

fun buildAdminSlotServicesModel(scope: CoroutineScope) = AdminSlotServicesModel(scope, SlotAdminQueries(slotAdminService()))

// --- Detail služby ---

enum class SlotAdminTab { DAY, SCHEDULE, SETTINGS, HISTORY }

class AdminSlotServiceModel(
    scope: CoroutineScope,
    private val serviceId: Uuid,
    private val queries: SlotAdminQueries,
    private val mutations: SlotAdminMutations,
    resend: SlotAuditResend,
    private val onDeleted: () -> Unit,
    /** Záložka z adresy (`?tab=schedule`) — po založení služby se otevírá rovnou Rozvrh. */
    initialTab: SlotAdminTab = SlotAdminTab.DAY,
) : ScreenModel(scope) {
    var state: LoadState<SlotServiceDetail> by mutableStateOf(LoadState.Loading); private set
    var tab by mutableStateOf(initialTab)

    var isPublishing by mutableStateOf(false); private set
    var showDelete by mutableStateOf(false); private set
    var refundOnDelete by mutableStateOf(true)
    var isDeleting by mutableStateOf(false); private set

    val day = SlotDayModel(scope, serviceId, queries)
    val schedule = SlotScheduleModel(scope, serviceId, queries, mutations, onSaved = { reload() })
    val settings = SlotSettingsModel(scope, mutations, existingId = serviceId, onSaved = { reload() })
    val history = SlotHistoryModel(scope, serviceId, queries, resend)

    val detail: SlotServiceDetail? get() = (state as? LoadState.Loaded)?.value

    fun load() {
        state = LoadState.Loading
        reload()
    }

    /** Po uložení: nový stav služby bez blikání načítání. */
    private fun reload() {
        scope.launch {
            try {
                queries.detail(serviceId)
                    .onRight { detail ->
                        state = LoadState.Loaded(detail)
                        schedule.reset(detail.rules, detail.exceptions)
                        settings.reset(detail.service)
                    }
                    .onLeft { if (detail == null) state = LoadState.Failed(slotAdminStrings.error(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (detail == null) state = LoadState.Failed(slotAdminStrings.loadError)
            }
        }
        day.load()
    }

    fun togglePublished() {
        val service = detail?.service ?: return
        val publish = !service.isPublished
        run(
            loading = { isPublishing = it },
            errorMessage = { slotAdminStrings.error(it) },
            block = { mutations.setPublished(serviceId, publish) },
            onSuccess = {
                showToast(if (publish) slotAdminStrings.publishedToast else slotAdminStrings.hiddenToast)
                reload()
            },
        )
    }

    fun askDelete() { showDelete = true; refundOnDelete = true }
    fun dismissDelete() { if (!isDeleting) showDelete = false }

    fun confirmDelete() {
        run(
            loading = { isDeleting = it },
            errorMessage = { slotAdminStrings.error(it) },
            block = { mutations.delete(serviceId, refundOnDelete) },
            onSuccess = {
                showDelete = false
                onDeleted()
            },
        )
    }
}

fun buildAdminSlotServiceModel(
    scope: CoroutineScope,
    serviceId: Uuid,
    initialTab: SlotAdminTab,
    onDeleted: () -> Unit,
): AdminSlotServiceModel {
    val rpc = slotAdminService()
    val resend = SlotAuditResend(getService<AdminServiceInterface>(RpcSerializersModules))
    return AdminSlotServiceModel(scope, serviceId, SlotAdminQueries(rpc), SlotAdminMutations(rpc), resend, onDeleted, initialTab)
}

// --- Historie ---

/**
 * Historie služby — stejná tabulka i chování jako historie akce
 * (`AdminEventDetailModel`), jen bez sbalování: je to samostatná záložka.
 * Načítá se až při prvním otevření záložky.
 */
class SlotHistoryModel(
    scope: CoroutineScope,
    private val serviceId: Uuid,
    private val queries: SlotAdminQueries,
    private val auditResend: SlotAuditResend,
) : ScreenModel(scope) {
    var page: AuditLogPage? by mutableStateOf(null); private set
    var error: String? by mutableStateOf(null); private set
    var currentPage by mutableIntStateOf(0); private set
    var category: AuditCategory? by mutableStateOf(null); private set
    var isLoading by mutableStateOf(false); private set
    var resendPending: AuditEvent? by mutableStateOf(null)
    var resendingId: Uuid? by mutableStateOf(null); private set
    private var job: Job? = null

    /** Při prvním zobrazení záložky; další otevření drží načtenou stránku. */
    fun ensureLoaded() { if (page == null && !isLoading) load() }

    fun goToPage(value: Int) {
        currentPage = value
        load()
    }

    fun changeCategory(value: AuditCategory?) {
        category = value
        currentPage = 0
        load()
    }

    fun load() {
        job?.cancel()
        isLoading = true
        error = null
        val requestedPage = currentPage
        val requestedCategory = category
        job = scope.launch {
            try {
                queries.auditLog(serviceId, requestedPage, requestedCategory)
                    .onRight { page = it }
                    .onLeft { error = slotAdminStrings.error(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = slotAdminStrings.loadError
            } finally {
                isLoading = false
            }
        }
    }

    /** Po odeslání se historie přenačte — přibude řádek nového pokusu. */
    fun resend(entry: AuditEvent) {
        resendingId = entry.id
        run(
            errorMessage = { it.localizedMessage(currentStrings) },
            block = { auditResend.resend(entry.id) },
            onSuccess = { recipient -> showToast(currentStrings.auditResendSuccess(recipient)) },
        ).invokeOnCompletion { resendingId = null; resendPending = null; load() }
    }
}

// --- Den ---

class SlotDayModel(
    scope: CoroutineScope,
    private val serviceId: Uuid,
    private val queries: SlotAdminQueries,
) : ScreenModel(scope) {
    var date: LocalDate by mutableStateOf(browserToday()); private set
    var state: LoadState<SlotDayOverview> by mutableStateOf(LoadState.Loading); private set
    private var job: Job? = null

    fun load() {
        job?.cancel()
        state = LoadState.Loading
        val requested = date
        job = scope.launch {
            state = try {
                queries.day(serviceId, requested).fold(
                    ifLeft = { LoadState.Failed(slotAdminStrings.error(it)) },
                    ifRight = { LoadState.Loaded(it) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                LoadState.Failed(slotAdminStrings.loadError)
            }
        }
    }

    fun shift(days: Int) = goTo(shiftDay(date, days))
    fun goToToday() = goTo(browserToday())

    fun goTo(value: LocalDate) {
        date = value
        load()
    }
}

// --- Rozvrh ---

class SlotScheduleModel(
    scope: CoroutineScope,
    private val serviceId: Uuid,
    private val queries: SlotAdminQueries,
    private val mutations: SlotAdminMutations,
    private val onSaved: () -> Unit,
) : ScreenModel(scope) {
    var draft by mutableStateOf(ScheduleDraft(emptyList(), emptyList()))
    var problems: List<ScheduleProblem> by mutableStateOf(emptyList()); private set
    var isSaving by mutableStateOf(false); private set
    /** Rezervace mimo nový rozvrh — dokud je neprázdné, čeká se na potvrzení. */
    var affected: List<SlotReservationRow> by mutableStateOf(emptyList()); private set
    private var pending: Pair<List<AvailabilityRule>, List<AvailabilityException>>? = null

    fun reset(rules: List<AvailabilityRule>, exceptions: List<AvailabilityException>) {
        draft = scheduleDraftOf(rules, exceptions)
        problems = emptyList()
    }

    fun edit(change: (ScheduleDraft) -> ScheduleDraft) {
        draft = change(draft)
        problems = emptyList()
    }

    /** Nejdřív se zeptá, koho se změna dotkne; teprve bez dotčených rezervací (nebo po potvrzení) ukládá. */
    fun save() {
        draft.toSchedule(serviceId)
            .onLeft { problems = it }
            .onRight { schedule ->
                problems = emptyList()
                run(
                    loading = { isSaving = it },
                    errorMessage = { slotAdminStrings.error(it) },
                    block = { queries.previewSchedule(serviceId, schedule.first, schedule.second) },
                    onSuccess = { rows ->
                        if (rows.isEmpty()) store(schedule) else {
                            pending = schedule
                            affected = rows
                        }
                    },
                    onError = { error -> (error as? SlotAdminError.InvalidSchedule)?.let { problems = it.problems } != null },
                )
            }
    }

    fun confirmAffected() {
        val schedule = pending ?: return
        store(schedule)
    }

    fun dismissAffected() {
        pending = null
        affected = emptyList()
    }

    private fun store(schedule: Pair<List<AvailabilityRule>, List<AvailabilityException>>) {
        run(
            loading = { isSaving = it },
            errorMessage = { slotAdminStrings.error(it) },
            block = { mutations.saveSchedule(serviceId, schedule.first, schedule.second) },
            onSuccess = {
                dismissAffected()
                showToast(slotAdminStrings.saved)
                onSaved()
            },
            onError = { error -> (error as? SlotAdminError.InvalidSchedule)?.let { problems = it.problems } != null },
        )
    }
}

// --- Nastavení (i založení nové služby) ---

class SlotSettingsModel(
    scope: CoroutineScope,
    private val mutations: SlotAdminMutations,
    /** null = zakládá se nová služba. */
    private val existingId: Uuid?,
    private val onSaved: () -> Unit = {},
    private val onCreated: (Uuid) -> Unit = {},
) : ScreenModel(scope) {
    var form by mutableStateOf(
        if (existingId == null) newServiceForm(slotAdminStrings.defaultChildrenField, slotAdminStrings.defaultNoteField)
        else SlotServiceForm()
    )
    var problems: List<SlotServiceProblem> by mutableStateOf(emptyList()); private set
    var isSaving by mutableStateOf(false); private set
    /**
     * Nová služba už je založená a čeká se na přesměrování do detailu. Tlačítko zůstává
     * vypnuté, aby druhé kliknutí nezaložilo tutéž službu znovu (s jiným id).
     */
    var isCreated by mutableStateOf(false); private set

    val isNew: Boolean get() = existingId == null
    val canSave: Boolean get() = !isSaving && !isCreated

    fun reset(service: SlotService) {
        form = formOf(service)
        problems = emptyList()
    }

    fun edit(change: (SlotServiceForm) -> SlotServiceForm) {
        form = change(form)
        // Chyby se přepočítají až u dalšího uložení, jen opravené zmizí hned.
        if (problems.isNotEmpty()) problems = problems.intersect(formProblems(form).toSet()).toList()
    }

    fun save() {
        if (!canSave) return
        val found = formProblems(form)
        if (found.isNotEmpty()) {
            problems = found
            return
        }
        val service = form.toService(existingId ?: Uuid.random())
        val onError = { error: SlotAdminError -> (error as? SlotAdminError.InvalidService)?.let { problems = it.problems } != null }
        if (existingId == null) {
            // Zamknout hned, ne až v korutině — dvojklik by jinak stihl odeslat dvě založení.
            isSaving = true
            run(
                loading = { isSaving = it },
                errorMessage = { slotAdminStrings.error(it) },
                block = { mutations.create(service) },
                onSuccess = { id ->
                    isCreated = true
                    onCreated(id)
                },
                onError = onError,
            )
        } else {
            run(
                loading = { isSaving = it },
                errorMessage = { slotAdminStrings.error(it) },
                block = { mutations.update(service) },
                onSuccess = {
                    showToast(slotAdminStrings.saved)
                    onSaved()
                },
                onError = onError,
            )
        }
    }
}

fun buildNewSlotServiceModel(scope: CoroutineScope, onCreated: (Uuid) -> Unit) =
    SlotSettingsModel(scope, SlotAdminMutations(slotAdminService()), existingId = null, onCreated = onCreated)
