package cz.svitaninymburk.projects.reservations.ui.dashboard

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.activeFilterName
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.filterDashboardEvents
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.filterDashboardSeries
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.parseUuidOrNull
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
}
