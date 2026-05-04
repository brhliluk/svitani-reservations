package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

object ICalGenerator {

    private val tz = TimeZone.of("Europe/Prague")

    fun forInstance(instance: EventInstance, reservationId: Uuid, appBaseUrl: String): String =
        buildVCal(
            uid = "$reservationId@rezervace.svitaninymburk.cz",
            summary = instance.title,
            description = instance.description,
            url = "$appBaseUrl/reservation/$reservationId",
            dtStart = instance.startDateTime,
            dtEnd = instance.endDateTime,
            rrule = null,
        )

    fun forSeries(series: EventSeries, reservationId: Uuid, appBaseUrl: String): String {
        val dow = series.lessonDayOfWeek
        val startTime = series.lessonStartTime
        val endTime = series.lessonEndTime

        return if (dow != null && startTime != null && endTime != null) {
            buildVCal(
                uid = "$reservationId@rezervace.svitaninymburk.cz",
                summary = series.title,
                description = series.description,
                url = "$appBaseUrl/reservation/$reservationId",
                dtStart = LocalDateTime(series.startDate, startTime),
                dtEnd = LocalDateTime(series.startDate, endTime),
                rrule = "RRULE:FREQ=WEEKLY;COUNT=${series.lessonCount}",
            )
        } else {
            buildAllDayVCal(
                uid = "$reservationId@rezervace.svitaninymburk.cz",
                summary = series.title,
                description = series.description,
                url = "$appBaseUrl/reservation/$reservationId",
                startDate = series.startDate,
                endDate = series.endDate,
            )
        }
    }

    private fun buildVCal(
        uid: String,
        summary: String,
        description: String,
        url: String,
        dtStart: LocalDateTime,
        dtEnd: LocalDateTime,
        rrule: String?,
    ): String {
        val now = utcBasic(Clock.System.now().toLocalDateTime(tz))
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Svítání Nymburk//Rezervace//CS")
            appendLine("METHOD:REQUEST")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:$now")
            appendLine("DTSTART;TZID=Europe/Prague:${localBasic(dtStart)}")
            appendLine("DTEND;TZID=Europe/Prague:${localBasic(dtEnd)}")
            if (rrule != null) appendLine(rrule)
            appendLine("SUMMARY:${icalEscape(summary)}")
            appendLine("DESCRIPTION:${icalEscape(description)}")
            appendLine("URL:$url")
            appendLine("END:VEVENT")
            append("END:VCALENDAR")
        }
    }

    private fun buildAllDayVCal(
        uid: String,
        summary: String,
        description: String,
        url: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): String {
        val now = utcBasic(Clock.System.now().toLocalDateTime(tz))
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Svítání Nymburk//Rezervace//CS")
            appendLine("METHOD:REQUEST")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:$now")
            appendLine("DTSTART;VALUE=DATE:${dateBasic(startDate)}")
            appendLine("DTEND;VALUE=DATE:${dateBasic(endDate)}")
            appendLine("SUMMARY:${icalEscape(summary)}")
            appendLine("DESCRIPTION:${icalEscape(description)}")
            appendLine("URL:$url")
            appendLine("END:VEVENT")
            append("END:VCALENDAR")
        }
    }

    private fun localBasic(dt: LocalDateTime): String =
        "%04d%02d%02dT%02d%02d%02d".format(dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute, dt.second)

    private fun dateBasic(d: LocalDate): String =
        "%04d%02d%02d".format(d.year, d.monthNumber, d.dayOfMonth)

    private fun utcBasic(dt: LocalDateTime): String = localBasic(dt) + "Z"

    private fun icalEscape(s: String): String =
        s.replace("\\", "\\\\").replace(",", "\\,").replace("\n", "\\n")
}
