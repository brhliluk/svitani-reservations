package cz.svitaninymburk.projects.reservations.ui.dashboard.usecase

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * Hledání na rozcestníku. Dotaz se rozpadne na slova a každé musí sedět:
 * text se hledá v titulku (popis jen jako záloha, viz [searchDashboard]), datum ("15.10.", "15. 10. 2026", "2026-10-15",
 * "dnes", "zítra"), den v týdnu ("středa", "víkend") a měsíc ("říjen", "října")
 * se porovnává s termínem akce, u kurzu s kterýmkoli dnem jeho lekcí.
 */
sealed interface SearchTerm {
    data class Text(val key: String) : SearchTerm
    data class Date(val day: Int, val month: Int, val year: Int? = null) : SearchTerm
    data class Weekdays(val days: Set<DayOfWeek>) : SearchTerm
    data class Month(val month: Int) : SearchTerm
}

private const val ACCENTED = "áäčďéěíĺľňóôöŕřšťúůüýž"
private const val PLAIN = "aacdeeillnooorrstuuuyz"

/** Text pro porovnání při hledání — bez velkých písmen a bez diakritiky, ať "joga" najde "Jóga". */
fun searchKey(text: String): String =
    text.lowercase().map { c -> ACCENTED.indexOf(c).let { if (it >= 0) PLAIN[it] else c } }.joinToString("")

private val WEEKDAYS: Map<String, Set<DayOfWeek>> = mapOf(
    "pondeli" to setOf(DayOfWeek.MONDAY), "monday" to setOf(DayOfWeek.MONDAY),
    "utery" to setOf(DayOfWeek.TUESDAY), "tuesday" to setOf(DayOfWeek.TUESDAY),
    "streda" to setOf(DayOfWeek.WEDNESDAY), "stredu" to setOf(DayOfWeek.WEDNESDAY), "wednesday" to setOf(DayOfWeek.WEDNESDAY),
    "ctvrtek" to setOf(DayOfWeek.THURSDAY), "thursday" to setOf(DayOfWeek.THURSDAY),
    "patek" to setOf(DayOfWeek.FRIDAY), "friday" to setOf(DayOfWeek.FRIDAY),
    "sobota" to setOf(DayOfWeek.SATURDAY), "sobotu" to setOf(DayOfWeek.SATURDAY), "saturday" to setOf(DayOfWeek.SATURDAY),
    "nedele" to setOf(DayOfWeek.SUNDAY), "nedeli" to setOf(DayOfWeek.SUNDAY), "sunday" to setOf(DayOfWeek.SUNDAY),
    "vikend" to setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "weekend" to setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
)

/** Měsíce v 1. i 2. pádě ("říjen" i "na 15. října") a anglicky. */
private val MONTHS: Map<String, Int> = listOf(
    listOf("leden", "ledna", "january"),
    listOf("unor", "unora", "february"),
    listOf("brezen", "brezna", "march"),
    listOf("duben", "dubna", "april"),
    listOf("kveten", "kvetna", "may"),
    listOf("cerven", "cervna", "june"),
    listOf("cervenec", "cervence", "july"),
    listOf("srpen", "srpna", "august"),
    listOf("zari", "september"),
    listOf("rijen", "rijna", "october"),
    listOf("listopad", "listopadu", "november"),
    listOf("prosinec", "prosince", "december"),
).flatMapIndexed { index, names -> names.map { it to index + 1 } }.toMap()

private val DOTTED_DATE = Regex("""^(\d{1,2})\.(\d{1,2})\.?(\d{4})?$""")
private val ISO_DATE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")

/** "15. 10. 2026" se píše s mezerami — slepíme ho, ať je to jedno slovo. */
private val SPACE_AFTER_DATE_DOT = Regex("""(\d)\.\s+(?=\d)""")

private fun dateTerm(day: Int, month: Int, year: Int?): SearchTerm.Date? =
    if (day in 1..31 && month in 1..12) SearchTerm.Date(day, month, year) else null

private fun parseTerm(word: String, today: LocalDate): SearchTerm {
    DOTTED_DATE.matchEntire(word)?.let { m ->
        val (day, month, year) = m.destructured
        dateTerm(day.toInt(), month.toInt(), year.toIntOrNull())?.let { return it }
    }
    ISO_DATE.matchEntire(word)?.let { m ->
        val (year, month, day) = m.destructured
        dateTerm(day.toInt(), month.toInt(), year.toInt())?.let { return it }
    }
    when (word) {
        "dnes", "today" -> return SearchTerm.Date(today.day, today.month.number, today.year)
        "zitra", "tomorrow" -> today.plus(1, DateTimeUnit.DAY).let { return SearchTerm.Date(it.day, it.month.number, it.year) }
    }
    WEEKDAYS[word]?.let { return SearchTerm.Weekdays(it) }
    MONTHS[word]?.let { return SearchTerm.Month(it) }
    return SearchTerm.Text(word)
}

fun parseSearch(query: String, today: LocalDate): List<SearchTerm> =
    searchKey(query)
        .replace(SPACE_AFTER_DATE_DOT, "$1.")
        .split(' ', '\t', '\n')
        .filter { it.isNotEmpty() }
        .map { parseTerm(it, today) }

private fun SearchTerm.matchesDate(date: LocalDate): Boolean = when (this) {
    is SearchTerm.Text -> false
    is SearchTerm.Date -> date.day == day && date.month.number == month && (year == null || date.year == year)
    is SearchTerm.Weekdays -> date.dayOfWeek in days
    is SearchTerm.Month -> date.month.number == month
}

private fun words(text: String): List<String> =
    text.splitWhen { !it.isLetterOrDigit() }.filter { it.isNotEmpty() }

private fun String.splitWhen(isSeparator: (Char) -> Boolean): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    for (c in this) {
        if (isSeparator(c)) { result += current.toString(); current.clear() } else current.append(c)
    }
    result += current.toString()
    return result
}

/**
 * Text musí být v některém slově z [texts] (kdekoli, i uprostřed), datum musí sedět
 * na některý z [dates]. Že krátký dotaz ("tan") najde i "dostanete", nevadí — popis
 * se prohledává jen jako záloha, viz [searchDashboard].
 */
private fun List<SearchTerm>.matches(texts: List<String>, dates: Sequence<LocalDate>): Boolean {
    val haystack = texts.flatMap { words(searchKey(it)) }
    return all { term ->
        if (term is SearchTerm.Text) {
            words(term.key).all { part -> haystack.any { part in it } }
        } else {
            dates.any { term.matchesDate(it) }
        }
    }
}

/** Kurz bez data dlouhého přes rok je nesmysl — strop, ať rozbitá data nezaseknou psaní. */
private const val MAX_SERIES_DAYS = 400

/** Dny, kdy se kurz koná: celý rozsah, případně jen jeho den v týdnu. */
private fun EventSeries.lessonDays(): Sequence<LocalDate> =
    generateSequence(startDate) { it.plus(1, DateTimeUnit.DAY) }
        .take(MAX_SERIES_DAYS)
        .takeWhile { it <= endDate }
        .filter { lessonDayOfWeek == null || it.dayOfWeek == lessonDayOfWeek }

data class DashboardSearchResult(val events: List<EventInstance>, val series: List<EventSeries>)

/**
 * Hledá se v titulcích; popis přijde na řadu, jen když dotaz nesedí na žádný titulek
 * (akce ani kurzu). Jinak by se mezi akce s hledaným slovem v názvu míchaly ty,
 * které ho mají jen někde v popisu.
 */
fun searchDashboard(
    events: List<EventInstance>,
    series: List<EventSeries>,
    query: String,
    today: LocalDate,
): DashboardSearchResult {
    val terms = parseSearch(query, today)
    if (terms.isEmpty()) return DashboardSearchResult(events, series)

    fun search(withDescription: Boolean) = DashboardSearchResult(
        events = events.filter {
            terms.matches(listOfNotNull(it.title, it.description.takeIf { withDescription }), sequenceOf(it.startDateTime.date))
        },
        series = series.filter {
            terms.matches(listOfNotNull(it.title, it.description.takeIf { withDescription }), it.lessonDays())
        },
    )

    val byTitle = search(withDescription = false)
    return if (byTitle.events.isNotEmpty() || byTitle.series.isNotEmpty()) byTitle else search(withDescription = true)
}

fun searchDashboardEvents(events: List<EventInstance>, query: String, today: LocalDate): List<EventInstance> =
    searchDashboard(events, emptyList(), query, today).events

fun searchDashboardSeries(series: List<EventSeries>, query: String, today: LocalDate): List<EventSeries> =
    searchDashboard(emptyList(), series, query, today).series

