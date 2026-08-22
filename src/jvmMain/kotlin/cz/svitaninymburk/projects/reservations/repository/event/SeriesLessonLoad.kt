package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.reservation.ReferenceDbDiscriminator
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutsTable
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.util.dbQuery
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.Uuid

/**
 * Stavy, ve kterých rezervace místo nedrží: zrušená a odmítnutá ho nedrží nikdy,
 * čekatel v pořadníku ho teprve může dostat. Jediná definice v celém projektu —
 * SQL i Kotlin filtry se od ní odvozují, aby se nemohly rozejít.
 */
val INACTIVE_RESERVATION_STATUSES: List<Reservation.Status> = listOf(
    Reservation.Status.CANCELLED,
    Reservation.Status.REJECTED,
    Reservation.Status.WAITLISTED,
)

/**
 * Kolik míst na dané lekci drží účastníci kurzu: aktivní přihlášky na sérii
 * mínus ti, kdo se z té konkrétní lekce omluvili.
 *
 * Přihláška na kurz je jedna rezervace s [Reference.Series]; na jednotlivé lekce
 * se nezapisuje nic, takže obsazenost lekce se musí dopočítat.
 */
interface SeriesLessonLoad {
    /** instanceId → počet míst obsazených přihláškami na kurz, po odečtení omluvenek. */
    suspend fun forInstances(instances: List<EventInstance>): Map<Uuid, Int>
}

/** Pohodlí pro místa, která řeší jednu lekci. */
suspend fun SeriesLessonLoad.forInstance(instance: EventInstance): Int =
    forInstances(listOf(instance))[instance.id] ?: 0

class ExposedSeriesLessonLoad : SeriesLessonLoad {

    override suspend fun forInstances(instances: List<EventInstance>): Map<Uuid, Int> {
        val lessons = instances.filter { it.seriesId != null }
        if (lessons.isEmpty()) return instances.associate { it.id to 0 }

        val seriesIds = lessons.mapNotNull { it.seriesId }.distinct()
        val instanceIds = lessons.map { it.id }

        return dbQuery {
            // Jeden dotaz na celou dávku — dashboard načítá všechny instance naráz.
            val seatsPerSeries: Map<Uuid, Int> = ReservationsTable
                .select(ReservationsTable.referenceId, ReservationsTable.seatCount.sum())
                .where {
                    (ReservationsTable.referenceType eq ReferenceDbDiscriminator.SERIES) and
                        (ReservationsTable.referenceId inList seriesIds) and
                        (ReservationsTable.status notInList INACTIVE_RESERVATION_STATUSES)
                }
                .groupBy(ReservationsTable.referenceId)
                .associate { row ->
                    row[ReservationsTable.referenceId] to (row[ReservationsTable.seatCount.sum()] ?: 0)
                }

            val optedOutPerInstance: Map<Uuid, Int> = SeriesLessonOptOutsTable
                .join(
                    ReservationsTable,
                    JoinType.INNER,
                    onColumn = SeriesLessonOptOutsTable.reservationId,
                    otherColumn = ReservationsTable.id,
                )
                .select(SeriesLessonOptOutsTable.instanceId, ReservationsTable.seatCount.sum())
                .where {
                    (SeriesLessonOptOutsTable.instanceId inList instanceIds) and
                        (ReservationsTable.status notInList INACTIVE_RESERVATION_STATUSES)
                }
                .groupBy(SeriesLessonOptOutsTable.instanceId)
                .associate { row ->
                    row[SeriesLessonOptOutsTable.instanceId] to (row[ReservationsTable.seatCount.sum()] ?: 0)
                }

            instances.associate { instance ->
                instance.id to instance.loadFrom(seatsPerSeries, optedOutPerInstance)
            }
        }
    }
}

class InMemorySeriesLessonLoad(
    private val reservationRepository: ReservationRepository,
    private val optOutRepository: SeriesLessonOptOutRepository,
) : SeriesLessonLoad {

    override suspend fun forInstances(instances: List<EventInstance>): Map<Uuid, Int> {
        val seriesIds = instances.mapNotNull { it.seriesId }.distinct()

        val seatsPerSeries: Map<Uuid, Int> = seriesIds.associateWith { seriesId ->
            reservationRepository.findByReference(Reference.Series(seriesId))
                .filter { it.status !in INACTIVE_RESERVATION_STATUSES }
                .sumOf { it.seatCount }
        }

        val optedOutPerInstance: Map<Uuid, Int> = instances.associate { instance ->
            instance.id to optOutRepository.findByInstance(instance.id)
                .mapNotNull { reservationRepository.findById(it.reservationId) }
                .filter { it.status !in INACTIVE_RESERVATION_STATUSES }
                .sumOf { it.seatCount }
        }

        return instances.associate { instance ->
            instance.id to instance.loadFrom(seatsPerSeries, optedOutPerInstance)
        }
    }
}

/**
 * Společný výpočet pro obě implementace. `coerceAtLeast(0)` je pojistka proti
 * nekonzistentním datům (omluvenka na lekci z jiné série); logicky je odečet
 * vždy podmnožinou součtu.
 */
private fun EventInstance.loadFrom(
    seatsPerSeries: Map<Uuid, Int>,
    optedOutPerInstance: Map<Uuid, Int>,
): Int {
    val series = seriesId ?: return 0
    val enrolled = seatsPerSeries[series] ?: 0
    val optedOut = optedOutPerInstance[id] ?: 0
    return (enrolled - optedOut).coerceAtLeast(0)
}
