package cz.svitaninymburk.projects.reservations.ui.reservation

import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.NumberValue
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.CustomFieldValidation
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.isCustomFieldValid
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/*
 * ============================================================================
 *  Povinná vlastní pole: co dělá dnešní validace vs. co navrhuju
 * ============================================================================
 *
 * Tenhle soubor je podklad k rozhodnutí, ne hotová oprava. Obě varianty žijí
 * v `isCustomFieldValid` za přepínačem [CustomFieldValidation], aby se dalo obojí
 * proklikat v běžící aplikaci (`?validation=proposed`, viz
 * `ValidationVariantSwitch.kt`). Až se jedna varianta vybere, druhá i s přepínačem
 * zmizí a tenhle spec se přepíše na tu zvolenou.
 *
 * Dnešní podoba:
 *
 *     if (!field.isRequired) return true
 *     when (field) {
 *         is BooleanFieldDefinition -> true
 *         is TextFieldDefinition, is NumberFieldDefinition ->
 *             value != null && value.toString().isNotBlank()
 *         is TimeRangeFieldDefinition -> ...
 *     }
 *
 * Tři místa, kde to pouští dál, než by mělo:
 *
 *  A) `value.toString()` je toString data classy, takže vrací
 *     "TextValue(fieldKey=pozn, value=)" a blank není nikdy. Kontrola tak
 *     reálně stojí jen na `value != null`. `renderCustomField` přitom zapisuje
 *     hodnotu do mapy při každém stisku klávesy a nikdy ji neodebere, takže
 *     vyplnit povinné pole a zase ho vymazat projde.
 *
 *  B) Povinné číslo: vymazané pole se v `renderCustomField` uloží jako 0f
 *     (`this.value?.toFloatOrNull() ?: 0f`). Validace tedy nemá jak rozeznat
 *     "smazal jsem to" od "napsal jsem nulu". Tohle samotná validace neopraví —
 *     patří to do CustomFields.kt (nezapisovat prázdnou hodnotu / odebrat klíč).
 *
 *  C) Povinné zaškrtávátko projde i nezaškrtnuté. Dřív jsem to popsal jako
 *     záměr ("povinné = má se zobrazit"), ale okolní kód mluví proti:
 *     `renderCustomField` u něj nastavuje HTML `required(field.isRequired)` a
 *     kreslí červenou hvězdičku. Native `required` se nikdy neuplatní, protože
 *     formulář se neodesílá nativně (submit je preventDefault a tlačítko se
 *     jen zašedne), takže jediná skutečná brána je tahle funkce. U souhlasu
 *     s podmínkami je "povinné, ale nezaškrtnuté projde" nejspíš chyba.
 *
 * Časový rozsah je dnes v pořádku a návrh ho nemění.
 */

private fun target(fields: List<CustomFieldDefinition>) = ReservationTarget.Instance(
    EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Akce",
        description = "",
        startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 6, 1, 12, 0),
        price = 100.0,
        capacity = 10,
        occupiedSpots = 0,
        customFields = fields,
    )
)

// --- Pomůcky, aby se oba pohledy vyhodnocovaly na úplně stejném vstupu ---

private class Case(
    val name: String,
    // Ne `field` — uvnitř getteru níž by to Kotlin bral jako backing field.
    val definition: CustomFieldDefinition,
    val value: CustomFieldValue?,
) {
    private val target = target(listOf(definition))
    val current: Boolean get() = isCustomFieldValid(definition, value, target, CustomFieldValidation.CURRENT)
    val proposed: Boolean get() = isCustomFieldValid(definition, value, target, CustomFieldValidation.PROPOSED)
}

private val TEXT = TextFieldDefinition(key = "pozn", label = "Poznámka", isRequired = true)
private val NUMBER = NumberFieldDefinition(key = "deti", label = "Počet dětí", isRequired = true)
private val NUMBER_RANGE = NumberFieldDefinition(key = "vek", label = "Věk", isRequired = true, min = 1, max = 18)
private val CHECK = BooleanFieldDefinition(key = "souhlas", label = "Souhlasím s podmínkami", isRequired = true)
private val RANGE = TimeRangeFieldDefinition(key = "cas", label = "Čas", isRequired = true)

/**
 * A) Povinný text. Rozdíl je jen u vyplněného-a-vymazaného pole; nedotčené
 * pole blokuje odeslání i dnes.
 */
class RequiredTextFieldSpec {

    private val untouched = Case("nedotčené", TEXT, null)
    private val cleared = Case("vyplněné a vymazané", TEXT, TextValue("pozn", ""))
    private val spacesOnly = Case("jen mezery", TEXT, TextValue("pozn", "   "))
    private val filled = Case("vyplněné", TEXT, TextValue("pozn", "bez lepku"))

    @Test
    fun bothAgreeOnUntouchedAndFilled() {
        assertFalse(untouched.current); assertFalse(untouched.proposed)
        assertTrue(filled.current); assertTrue(filled.proposed)
    }

    @Test
    fun currentLetsThroughAClearedRequiredField() {
        assertTrue(cleared.current, "dnes projde — toString() obálky není blank")
        assertTrue(spacesOnly.current, "dnes projde i pole s mezerami")
    }

    @Test
    fun proposedBlocksAClearedRequiredField() {
        assertFalse(cleared.proposed)
        assertFalse(spacesOnly.proposed)
    }

    @Test
    fun proposedTreatsAWrongValueTypeAsEmpty() {
        // Kdyby se pod klíč textového pole dostala jiná hodnota, dnes projde.
        val mismatched = Case("špatný typ", TEXT, NumberValue("pozn", 5f))
        assertTrue(mismatched.current)
        assertFalse(mismatched.proposed)
    }
}

/**
 * B) Povinné číslo. Vymazané pole se do mapy ukládá jako 0f, takže obě verze
 * ho pustí — validace na to nemá informaci. Rozdíl je u chybějící hodnoty,
 * špatného typu a u hranic min/max.
 */
class RequiredNumberFieldSpec {

    @Test
    fun bothAgreeOnUntouchedAndFilled() {
        val untouched = Case("nedotčené", NUMBER, null)
        val filled = Case("vyplněné", NUMBER, NumberValue("deti", 2f))
        assertFalse(untouched.current); assertFalse(untouched.proposed)
        assertTrue(filled.current); assertTrue(filled.proposed)
    }

    @Test
    fun neitherVersionCanTellAClearedFieldFromATypedZero() {
        // renderCustomField zapisuje `this.value?.toFloatOrNull() ?: 0f`, takže
        // po vymazání pole v mapě leží 0f a nula je legitimní odpověď.
        // Opravit to jde jen u vstupu, ne tady.
        val cleared = Case("vymazané → 0f", NUMBER, NumberValue("deti", 0f))
        assertTrue(cleared.current)
        assertTrue(cleared.proposed)
    }

    @Test
    fun currentIgnoresMinAndMax() {
        // NumberFieldDefinition nese min/max, ale validace se na ně nedívá —
        // hlídá je jen maska vstupu (imaskNumeric), takže hodnota, která do
        // mapy přijde jinak než psaním, projde.
        val tooYoung = Case("pod min", NUMBER_RANGE, NumberValue("vek", 0f))
        val tooOld = Case("nad max", NUMBER_RANGE, NumberValue("vek", 99f))
        assertTrue(tooYoung.current)
        assertTrue(tooOld.current)
    }

    @Test
    fun proposedEnforcesMinAndMax() {
        assertFalse(Case("pod min", NUMBER_RANGE, NumberValue("vek", 0f)).proposed)
        assertFalse(Case("nad max", NUMBER_RANGE, NumberValue("vek", 99f)).proposed)
        // Hranice se počítají jako uvnitř.
        assertTrue(Case("na min", NUMBER_RANGE, NumberValue("vek", 1f)).proposed)
        assertTrue(Case("na max", NUMBER_RANGE, NumberValue("vek", 18f)).proposed)
    }
}

/**
 * C) Povinné zaškrtávátko. Tady je rozdíl největší: dnes projde vždy, návrh
 * vyžaduje zaškrtnutí. U souhlasu s podmínkami je to ten podstatný případ.
 */
class RequiredCheckboxSpec {

    private val untouched = Case("nedotčené", CHECK, null)
    private val unchecked = Case("odškrtnuté", CHECK, BooleanValue("souhlas", false))
    private val checked = Case("zaškrtnuté", CHECK, BooleanValue("souhlas", true))

    @Test
    fun currentAcceptsARequiredCheckboxInAnyState() {
        assertTrue(untouched.current)
        assertTrue(unchecked.current)
        assertTrue(checked.current)
    }

    @Test
    fun proposedRequiresItToBeChecked() {
        assertFalse(untouched.proposed)
        assertFalse(unchecked.proposed)
        assertTrue(checked.proposed)
    }

    @Test
    fun optionalCheckboxStaysOptionalInBothVersions() {
        val optional = BooleanFieldDefinition(key = "newsletter", label = "Novinky", isRequired = false)
        val case = Case("nepovinné, odškrtnuté", optional, BooleanValue("newsletter", false))
        assertTrue(case.current)
        assertTrue(case.proposed)
    }
}

/** Časový rozsah — návrh ho nemění, takže obě verze musí souhlasit ve všem. */
class RequiredTimeRangeUnchangedSpec {

    @Test
    fun bothVersionsAgreeOnEveryTimeRangeCase() {
        val cases = listOf(
            Case("nevyplněno", RANGE, null),
            Case("prázdný rozsah", RANGE, TimeRangeValue("cas", LocalTime(10, 0), LocalTime(10, 0))),
            Case("obrácený", RANGE, TimeRangeValue("cas", LocalTime(11, 0), LocalTime(10, 30))),
            Case("před začátkem", RANGE, TimeRangeValue("cas", LocalTime(9, 0), LocalTime(11, 0))),
            Case("po konci", RANGE, TimeRangeValue("cas", LocalTime(11, 0), LocalTime(13, 0))),
            Case("na krajích", RANGE, TimeRangeValue("cas", LocalTime(10, 0), LocalTime(12, 0))),
            Case("uvnitř", RANGE, TimeRangeValue("cas", LocalTime(10, 30), LocalTime(11, 30))),
        )
        cases.forEach { assertEquals(it.current, it.proposed, "rozsah '${it.name}' se rozešel") }
    }
}

/**
 * Souhrn: jediná místa, kde se návrh od dneška odchyluje, a všechna jen tak,
 * že přestane pouštět dál. Nic, co dnes neprojde, návrhem projít nezačne.
 */
class CurrentVersusProposedSummarySpec {

    private val everyCase = listOf(
        Case("text nedotčený", TEXT, null),
        Case("text vymazaný", TEXT, TextValue("pozn", "")),
        Case("text mezery", TEXT, TextValue("pozn", "   ")),
        Case("text vyplněný", TEXT, TextValue("pozn", "ok")),
        Case("text špatný typ", TEXT, NumberValue("pozn", 1f)),
        Case("číslo nedotčené", NUMBER, null),
        Case("číslo nula", NUMBER, NumberValue("deti", 0f)),
        Case("číslo vyplněné", NUMBER, NumberValue("deti", 3f)),
        Case("číslo pod min", NUMBER_RANGE, NumberValue("vek", 0f)),
        Case("číslo nad max", NUMBER_RANGE, NumberValue("vek", 99f)),
        Case("checkbox nedotčený", CHECK, null),
        Case("checkbox odškrtnutý", CHECK, BooleanValue("souhlas", false)),
        Case("checkbox zaškrtnutý", CHECK, BooleanValue("souhlas", true)),
        Case("rozsah nevyplněný", RANGE, null),
        Case("rozsah uvnitř", RANGE, TimeRangeValue("cas", LocalTime(10, 30), LocalTime(11, 30))),
    )

    @Test
    fun proposedOnlyEverTightens() {
        everyCase.forEach { case ->
            if (!case.current) {
                assertFalse(case.proposed, "'${case.name}' dnes neprojde, návrhem projít nesmí")
            }
        }
    }

    @Test
    fun exactlySevenCasesChange() {
        val changed = everyCase.filter { it.current != it.proposed }.map { it.name }
        assertEquals(
            listOf(
                "text vymazaný",
                "text mezery",
                "text špatný typ",
                "číslo pod min",
                "číslo nad max",
                "checkbox nedotčený",
                "checkbox odškrtnutý",
            ),
            changed,
        )
    }
}
