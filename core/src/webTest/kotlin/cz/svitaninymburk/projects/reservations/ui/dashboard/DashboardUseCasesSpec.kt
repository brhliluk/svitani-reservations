package cz.svitaninymburk.projects.reservations.ui.dashboard

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.activeFilterName
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.filterDashboardEvents
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.filterDashboardSeries
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.SearchTerm
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.parseSearch
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.parseUuidOrNull
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.searchDashboard
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.searchDashboardEvents
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.searchDashboardSeries
import cz.svitaninymburk.projects.reservations.util.humanReadable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

private val yogaId = Uuid.random()
private val danceId = Uuid.random()
private val springCourseId = Uuid.random()
private val today = LocalDate(2099, 5, 31)

private fun instance(title: String, definitionId: Uuid, seriesId: Uuid? = null) = EventInstance(
    id = Uuid.random(),
    definitionId = definitionId,
    seriesId = seriesId,
    title = title,
    description = "",
    startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
    endDateTime = LocalDateTime(2099, 6, 1, 11, 0),
    price = 100.0,
    capacity = 10,
)

private fun series(title: String, definitionId: Uuid, id: Uuid = Uuid.random()) = EventSeries(
    id = id,
    definitionId = definitionId,
    title = title,
    description = "",
    price = 400.0,
    capacity = 10,
    occupiedSpots = 0,
    startDate = LocalDate(2099, 6, 1),
    endDate = LocalDate(2099, 7, 1),
    lessonCount = 4,
)

private fun definition(title: String, id: Uuid) = EventDefinition(
    id = id,
    title = title,
    description = "",
    defaultPrice = 100.0,
    defaultCapacity = 10,
    defaultDuration = 1.hours,
)

class DashboardUseCasesSpec {

    private val yogaLesson = instance("Jóga – lekce", yogaId, springCourseId)
    private val yogaOneOff = instance("Jóga – ukázka", yogaId)
    private val danceOneOff = instance("Tanec", danceId)
    private val events = listOf(yogaLesson, yogaOneOff, danceOneOff)

    private val springCourse = series("Jarní jóga", yogaId, springCourseId)
    private val danceCourse = series("Taneční", danceId)
    private val allSeries = listOf(springCourse, danceCourse)

    private val definitions = listOf(definition("Jóga", yogaId), definition("Tanec", danceId))

    @Test
    fun withoutFilterEverythingStays() {
        assertEquals(events, filterDashboardEvents(events, null, null))
        assertEquals(allSeries, filterDashboardSeries(allSeries, null, null))
    }

    @Test
    fun definitionFilterKeepsOnlyItsEventsAndSeries() {
        assertEquals(listOf(yogaLesson, yogaOneOff), filterDashboardEvents(events, null, yogaId))
        assertEquals(listOf(springCourse), filterDashboardSeries(allSeries, null, yogaId))
    }

    @Test
    fun seriesFilterIsNarrowerThanDefinitionFilter() {
        // Obojí naráz: kurz vyhrává, takže z jógy zůstanou jen jeho lekce.
        assertEquals(listOf(yogaLesson), filterDashboardEvents(events, springCourseId, yogaId))
        assertEquals(listOf(springCourse), filterDashboardSeries(allSeries, springCourseId, yogaId))
    }

    @Test
    fun filterNameFollowsTheNarrowerFilter() {
        assertEquals("Jarní jóga", activeFilterName(allSeries, definitions, springCourseId, yogaId))
        assertEquals("Jóga", activeFilterName(allSeries, definitions, null, yogaId))
        assertNull(activeFilterName(allSeries, definitions, null, null))
    }

    @Test
    fun instanceFilterKeepsOnlyThatEventAndHidesCourses() {
        // Odkaz na lekci vyhrává i nad kurzem a šablonou, které by jinak ukázaly víc.
        assertEquals(listOf(yogaLesson), filterDashboardEvents(events, springCourseId, yogaId, yogaLesson.id))
        assertEquals(emptyList(), filterDashboardSeries(allSeries, springCourseId, yogaId, yogaLesson.id))
    }

    @Test
    fun instanceFilterNameShowsTitleAndStart() {
        assertEquals(
            "Jóga – lekce • ${yogaLesson.startDateTime.humanReadable}",
            activeFilterName(allSeries, definitions, null, null, events, yogaLesson.id),
        )
    }

    /** Termín, který už ve výpisu není (proběhl, zrušili ho), nemá čím filtr popsat. */
    @Test
    fun instanceFilterNameIsNullForUnknownInstance() {
        assertNull(activeFilterName(allSeries, definitions, null, null, events, Uuid.random()))
    }

    @Test
    fun filterNameIsNullForUnknownId() {
        assertNull(activeFilterName(allSeries, definitions, null, Uuid.random()))
    }

    /** `/?filter=nesmysl` nesmí shodit celý rozcestník — bereme to jako "bez filtru". */
    @Test
    fun malformedIdInTheUrlIsIgnored() {
        assertNull(parseUuidOrNull("nesmysl"))
        assertNull(parseUuidOrNull(null))
        assertEquals(yogaId, parseUuidOrNull(yogaId.toString()))
    }

    @Test
    fun searchIgnoresCaseAndDiacritics() {
        assertEquals(listOf(yogaLesson, yogaOneOff), searchDashboardEvents(events, "JOGA", today))
        assertEquals(listOf(springCourse), searchDashboardSeries(allSeries, "jarni", today))
    }

    @Test
    fun everySearchWordMustMatch() {
        assertEquals(listOf(yogaOneOff), searchDashboardEvents(events, "joga ukazka", today))
        assertEquals(emptyList(), searchDashboardEvents(events, "joga tanec", today))
    }

    @Test
    fun searchMatchesAlsoInsideWords() {
        assertEquals(listOf(danceOneOff), searchDashboardEvents(events, "anec", today))
        assertEquals(listOf(yogaOneOff), searchDashboardEvents(events, "kazk", today))
    }

    @Test
    fun shortSearchInTitleIsNotDilutedByDescriptions() {
        val mentionsIt = yogaOneOff.copy(description = "Když dostanete chuť, přijďte.")
        assertEquals(listOf(danceOneOff), searchDashboardEvents(listOf(mentionsIt, danceOneOff), "tan", today))
    }

    @Test
    fun titleMatchesHideDescriptionMatches() {
        val mentionsDance = yogaOneOff.copy(description = "Po józe tanec.")
        val found = searchDashboard(listOf(mentionsDance, danceOneOff), allSeries, "tanec", today)
        assertEquals(listOf(danceOneOff), found.events)
        assertEquals(listOf(danceCourse), found.series)
    }

    @Test
    fun titleMatchOnASeriesAlsoHidesDescriptionMatches() {
        val mentionsCourse = yogaOneOff.copy(description = "Ochutnávka z jarní jógy.")
        val found = searchDashboard(listOf(mentionsCourse), allSeries, "jarni", today)
        assertEquals(emptyList(), found.events)
        assertEquals(listOf(springCourse), found.series)
    }

    @Test
    fun descriptionIsSearchedWhenNoTitleMatches() {
        val withDescription = yogaOneOff.copy(description = "Vhodné pro těhotné.")
        assertEquals(listOf(withDescription), searchDashboardEvents(listOf(withDescription, danceOneOff), "tehotne", today))
    }

    @Test
    fun blankSearchKeepsEverything() {
        assertEquals(events, searchDashboardEvents(events, "", today))
        assertEquals(allSeries, searchDashboardSeries(allSeries, "   ", today))
    }

    // Akce v testu jsou 1. 6. 2099 (pondělí), jarní kurz 1. 6. – 1. 7. 2099.

    @Test
    fun dateIsUnderstoodInCommonForms() {
        val onFirstJune = listOf(SearchTerm.Date(1, 6, 2099))
        assertEquals(onFirstJune, parseSearch("1.6.2099", today))
        assertEquals(onFirstJune, parseSearch("1. 6. 2099", today))
        assertEquals(onFirstJune, parseSearch("2099-06-01", today))
        assertEquals(listOf(SearchTerm.Date(1, 6)), parseSearch("01.06.", today))
        assertEquals(listOf(SearchTerm.Date(1, 6)), parseSearch("1.6", today))
    }

    @Test
    fun relativeDaysUseToday() {
        assertEquals(listOf(SearchTerm.Date(31, 5, 2099)), parseSearch("dnes", today))
        assertEquals(listOf(SearchTerm.Date(1, 6, 2099)), parseSearch("zítra", today))
    }

    @Test
    fun eventIsFoundByDateWeekdayAndMonth() {
        assertEquals(events, searchDashboardEvents(events, "1.6.", today))
        assertEquals(events, searchDashboardEvents(events, "zítra", today))
        assertEquals(events, searchDashboardEvents(events, "pondělí", today))
        assertEquals(events, searchDashboardEvents(events, "června", today))
        assertEquals(emptyList(), searchDashboardEvents(events, "2.6.", today))
        assertEquals(emptyList(), searchDashboardEvents(events, "úterý", today))
    }

    @Test
    fun dateAndTextCombine() {
        assertEquals(listOf(danceOneOff), searchDashboardEvents(events, "tanec 1. 6.", today))
    }

    @Test
    fun seriesIsFoundByAnyDayOfItsLessons() {
        val mondayCourse = springCourse.copy(lessonDayOfWeek = DayOfWeek.MONDAY)
        assertEquals(listOf(mondayCourse), searchDashboardSeries(listOf(mondayCourse), "8.6.", today))
        assertEquals(emptyList(), searchDashboardSeries(listOf(mondayCourse), "9.6.", today))
        assertEquals(emptyList(), searchDashboardSeries(listOf(mondayCourse), "15.7.", today))
        // Bez dne v týdnu se kurz koná kterýkoli den svého rozsahu.
        assertEquals(listOf(springCourse), searchDashboardSeries(listOf(springCourse), "středa", today))
    }

    @Test
    fun invalidDateIsJustText() {
        assertEquals(listOf(SearchTerm.Text("45.13.")), parseSearch("45.13.", today))
    }
}
