package cz.svitaninymburk.projects.reservations.reservation

import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.NumberValue
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue

/**
 * Vlastní pole akce. Nepovinné pole projde vždy; u povinného se kontroluje
 * obsah podle typu:
 *
 *  - text musí mít neprázdný obsah — mezery se nepočítají,
 *  - číslo musí být vyplněné a v případě zadaných hranic i uvnitř nich
 *    (`imaskNumeric` u vstupu je jen pomůcka, `max` neomezuje),
 *  - zaškrtávátko musí být zaškrtnuté; povinný souhlas s podmínkami je přesně
 *    ten případ, pro který to platí,
 *  - časový rozsah musí být neprázdný (od < do) a celý uvnitř doby akce.
 *
 * Špatný typ hodnoty pro dané pole se bere jako nevyplněno. Nekontroluje se
 * `value != null`, ale skutečný obsah: `CustomFieldValue` jsou data classy, u
 * kterých `toString()` vrací `TextValue(fieldKey=..., value=)` a blank tedy
 * není nikdy — a `renderCustomField` zapisuje hodnotu do mapy při každém
 * stisku klávesy a nikdy ji neodebere, takže na přítomnost klíče se spolehnout
 * nelze.
 *
 * Totéž pravidlo hlídá formulář (tlačítko Rezervovat) i server při založení
 * rezervace, proto žije ve sdíleném modulu.
 */
fun isCustomFieldValid(
    field: CustomFieldDefinition,
    value: CustomFieldValue?,
    target: ReservationTarget,
): Boolean {
    if (!field.isRequired) return true
    return when (field) {
        is TextFieldDefinition -> (value as? TextValue)?.value?.isNotBlank() == true
        is NumberFieldDefinition -> {
            val number = (value as? NumberValue)?.value ?: return false
            val aboveMin = field.min?.let { number >= it.toFloat() } ?: true
            val belowMax = field.max?.let { number <= it.toFloat() } ?: true
            aboveMin && belowMax
        }
        is BooleanFieldDefinition -> (value as? BooleanValue)?.value == true
        is TimeRangeFieldDefinition -> {
            val range = value as? TimeRangeValue ?: return false
            val window = target.startDateTime.time..target.endDateTime.time
            range.from < range.to && range.from in window && range.to in window
        }
    }
}

/** První vlastní pole cíle, které hodnoty nesplňují, nebo null, když jsou všechna v pořádku. */
fun firstInvalidCustomField(
    target: ReservationTarget,
    values: Map<String, CustomFieldValue>,
): CustomFieldDefinition? = target.customFields.firstOrNull { !isCustomFieldValid(it, values[it.key], target) }

fun areCustomFieldsValid(
    target: ReservationTarget,
    values: Map<String, CustomFieldValue>,
): Boolean = firstInvalidCustomField(target, values) == null
